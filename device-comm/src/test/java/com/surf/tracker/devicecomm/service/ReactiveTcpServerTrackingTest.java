package com.surf.tracker.devicecomm.service;

import com.surf.tracker.devicecomm.pipeline.ConnectionSession;
import com.surf.tracker.devicecomm.pipeline.IngestPipeline;
import com.surf.tracker.devicecomm.pipeline.ProtocolRegistry;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.autoseeker.AutoseekerProtocol;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactiveTcpServerTrackingTest {
    private static Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.US_ASCII));
    }

    enum Outcome { SUCCESS, MALFORMED, PUBLICATION_FAILURE }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void processFrame_updatesRoutingAndActivityOnlyAfterSuccessfulProcessing(Outcome outcome) throws Exception {
        // given: real parser/processor and a deterministic worker-completion boundary.
        String valid = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        var completed = new CountDownLatch(1);
        var publisher = mock(EventPublisher.class);
        var connections = mock(RedisConnectionTracker.class);
        if (outcome == Outcome.PUBLICATION_FAILURE) {
            doThrow(new IllegalStateException("synthetic broker unavailable")).when(publisher).publish(any());
        }
        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(connections).updateLastSeen("connection");
        var registry = new ProtocolRegistry(List.of(new AutoseekerProtocol()));
        var pipeline = new IngestPipeline(registry, publisher, Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC)) {
            @Override
            public String ingest(Frame frame, Instant receivedAt, ConnectionSession session) {
                try {
                    return super.ingest(frame, receivedAt, session);
                } catch (RuntimeException error) {
                    // An exception prevents the subsequent routing/activity statements.
                    completed.countDown();
                    throw error;
                }
            }
        };
        var gateway = new ReactiveTcpServer(pipeline, registry, connections);
        // when
        if (outcome == Outcome.PUBLICATION_FAILURE) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    ReflectionTestUtils.invokeMethod(gateway, "processFrame", frame(valid), "connection", new ConnectionSession()))
                    .isInstanceOf(com.surf.tracker.shared.exception.InfrastructureException.class);
        } else {
            ReflectionTestUtils.invokeMethod(gateway, "processFrame",
                    frame(outcome == Outcome.MALFORMED ? valid.replace("2234.9273", "NaN") : valid),
                    "connection", new ConnectionSession());
        }
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        // then: no fixed sleep is used to infer worker completion.
        if (outcome == Outcome.SUCCESS) {
            var order = inOrder(publisher, connections);
            order.verify(publisher).publish(any());
            order.verify(connections).updateConnectionDevice("connection", "8168000008");
            order.verify(connections).updateLastSeen("connection");
        } else {
            org.mockito.Mockito.verifyNoInteractions(connections);
            if (outcome == Outcome.MALFORMED) verifyNoInteractions(publisher);
            else verify(publisher).publish(any());
        }
    }
}
