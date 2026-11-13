package com.surf.tracker.core.application.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.surf.tracker.shared.commands.CommandWrite;
import com.surf.tracker.shared.commands.CommandWriteChannel;
import com.surf.tracker.shared.commands.CommandWriteResult;
import com.surf.tracker.shared.commands.WriteOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** One bounded request to an explicitly configured gateway. No redirects or retry loop. */
@Component
public class HttpCommandWriteChannel implements CommandWriteChannel, AutoCloseable {
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final URI endpoint;
    private final String secret;
    private final int timeoutSeconds;
    private final HttpClient client;

    public HttpCommandWriteChannel(ObjectMapper mapper,
            @Value("${tracking.commands.channel.enabled:false}") boolean enabled,
            @Value("${tracking.commands.channel.origin:}") String origin,
            @Value("${tracking.commands.channel.secret:}") String secret,
            @Value("${tracking.commands.channel.timeout-seconds:10}") int timeoutSeconds) {
        this.enabled = enabled;
        if (timeoutSeconds < 1 || timeoutSeconds > 20) throw new IllegalArgumentException("Command timeout must be 1–20 seconds");
        this.timeoutSeconds = timeoutSeconds;
        this.mapper = mapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.secret = secret;
        if (enabled) {
            URI address = URI.create(origin);
            boolean loopback = address.getHost() != null && List.of("localhost", "127.0.0.1", "[::1]", "::1").contains(address.getHost());
            if (address.getHost() == null || address.getUserInfo() != null || address.getQuery() != null
                || address.getFragment() != null || !(address.getPath().isEmpty() || address.getPath().equals("/"))
                || !("https".equals(address.getScheme()) || "http".equals(address.getScheme()) && loopback)
                || secret == null || !secret.matches("[!-~]{32,256}"))
                throw new IllegalArgumentException("Command channel requires HTTPS (or loopback HTTP), a bare origin and a strong shared secret");
            this.endpoint = address.resolve("/internal/command-writes");
        } else endpoint = null;
        this.client = enabled ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .followRedirects(HttpClient.Redirect.NEVER).build() : null;
    }

    @Override public CommandWriteResult write(CommandWrite command) {
        if (!enabled) return new CommandWriteResult(command.attemptId(), WriteOutcome.DISABLED);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(timeoutSeconds))
                .header("X-Command-Secret", secret).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(command))).build();
            pending = client.sendAsync(request, info -> new BoundedResponseBody());
            var response = pending.get(timeoutSeconds, TimeUnit.SECONDS);
            if (response.statusCode() == 200) {
                CommandWriteResult result = mapper.readValue(response.body(), CommandWriteResult.class);
                if (command.attemptId().equals(result.attemptId())) return result;
            } else if (response.statusCode() == 401 || response.statusCode() == 400 || response.statusCode() == 413 || response.statusCode() == 415)
                return new CommandWriteResult(command.attemptId(), WriteOutcome.REJECTED);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (Exception unavailableOrAmbiguous) { /* Never log payloads/secrets or replay an ambiguous write. */ }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
        return new CommandWriteResult(command.attemptId(), WriteOutcome.UNKNOWN);
    }

    @jakarta.annotation.PreDestroy
    @Override public void close() { if (client != null) client.close(); }

    private static final class BoundedResponseBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 1024) {
                    subscription.cancel(); result.completeExceptionally(new IllegalArgumentException("Oversized gateway result")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
