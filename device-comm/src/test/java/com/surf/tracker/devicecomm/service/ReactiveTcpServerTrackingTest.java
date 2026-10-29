package com.surf.tracker.devicecomm.service;

import com.surf.tracker.devicecomm.application.DeviceDataProcessor;
import com.surf.tracker.devicecomm.application.ParserFactory;
import com.surf.tracker.devicecomm.domain.DeviceDataPacket;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

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
    enum Outcome { SUCCESS, MALFORMED, PUBLICATION_FAILURE }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void processRawMessage_updatesRoutingAndActivityOnlyAfterSuccessfulProcessing(Outcome outcome) throws Exception {
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
        var processor = new DeviceDataProcessor(new ParserFactory(List.of(new AutoseekerProtocolParser())),
                publisher, Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC)) {
            @Override
            public void processDeviceData(DeviceDataPacket packet) {
                try {
                    super.processDeviceData(packet);
                } catch (RuntimeException error) {
                    // An exception prevents the subsequent routing/activity statements.
                    completed.countDown();
                    throw error;
                }
            }
        };
        var gateway = new ReactiveTcpServer(processor, connections);
        ReflectionTestUtils.setField(gateway, "messageDelimiter", "#");
        // when
        ReflectionTestUtils.invokeMethod(gateway, "processRawMessage",
                outcome == Outcome.MALFORMED ? valid.replace("2234.9273", "NaN") : valid,
                "connection", "127.0.0.1");
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        // then: no fixed sleep is used to infer worker completion.
        if (outcome == Outcome.SUCCESS) {
            var order = inOrder(publisher, connections);
            order.verify(publisher).publish(any());
            order.verify(connections).updateConnectionDevice("connection", "8168000008");
            order.verify(connections).updateLastSeen("connection");
        } else {
            verifyNoInteractions(connections);
            if (outcome == Outcome.MALFORMED) verifyNoInteractions(publisher);
            else verify(publisher).publish(any());
        }
    }
}
