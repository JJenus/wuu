package com.surf.tracker.devicecomm.pipeline;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.CommandCodec;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.devicecomm.protocol.autoseeker.AutoseekerProtocol;
import com.surf.tracker.devicecomm.protocol.gt06.Gt06Protocol;
import com.surf.tracker.devicecomm.protocol.model.DeviceHealth;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import com.surf.tracker.devicecomm.protocol.model.StatusReport;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.exception.InfrastructureException;
import com.surf.tracker.shared.pubsub.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class IngestPipelineTest {
    private static final String AUTOSEEKER = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
    private static final Instant RECEIVED = Instant.parse("2026-11-13T00:00:00Z");

    private final EventPublisher publisher = mock(EventPublisher.class);
    private IngestPipeline pipeline;

    private static Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        var registry = new ProtocolRegistry(List.of(new AutoseekerProtocol(), new Gt06Protocol()));
        pipeline = new IngestPipeline(registry, publisher, Clock.fixed(RECEIVED, ZoneOffset.UTC));
    }

    @Test
    void ingest_positionFrame_publishesLocationEventNamedAfterProtocol() {
        // given
        var session = new ConnectionSession();
        // when
        String deviceId = pipeline.ingest(frame(AUTOSEEKER), RECEIVED, session);
        // then
        var captured = ArgumentCaptor.forClass(LocationDataEvent.class);
        verify(publisher).publish(captured.capture());
        assertThat(deviceId).isEqualTo("8168000008");
        assertThat(captured.getValue().getDeviceId()).isEqualTo("8168000008");
        assertThat(captured.getValue().getProtocol()).isEqualTo("Autoseeker");
        assertThat(captured.getValue().getMetaData()).isEmpty();
    }

    @Test
    void ingest_firstAcceptedFrame_bindsProtocolAndDeviceToSession() {
        // given
        var session = new ConnectionSession();
        // when
        pipeline.ingest(frame(AUTOSEEKER), RECEIVED, session);
        // then
        assertThat(session.protocol().name()).isEqualTo("Autoseeker");
        assertThat(session.boundDeviceId()).isEqualTo("8168000008");
    }

    @Test
    void ingest_boundSession_keepsItsProtocolInsteadOfRedetecting() {
        // given: session already bound to GT06 although the frame would auto-detect as Autoseeker
        var session = new ConnectionSession();
        session.accept(new Gt06Protocol(), "8168000008");
        // when
        pipeline.ingest(frame(AUTOSEEKER), RECEIVED, session);
        // then
        var captured = ArgumentCaptor.forClass(LocationDataEvent.class);
        verify(publisher).publish(captured.capture());
        assertThat(captured.getValue().getProtocol()).isEqualTo("GT06");
    }

    @Test
    void ingest_heartbeatOrLogin_isAcknowledgedButNeverPublishedAsLocation() {
        // given
        var session = new ConnectionSession();
        // when
        pipeline.ingest(frame("*HQ,865205030330012,V0#"), RECEIVED, session);
        pipeline.ingest(frame("*HQ,865205030330012,HTBT#"), RECEIVED, session);
        // then
        verifyNoInteractions(publisher);
        assertThat(session.boundDeviceId()).isEqualTo("865205030330012");
    }

    @Test
    void ingest_deviceChangesIdentityOnSameConnection_rejectsAndPublishesNothingMore() {
        // given
        var session = new ConnectionSession();
        pipeline.ingest(frame(AUTOSEEKER), RECEIVED, session);
        // when / then
        assertThatThrownBy(() -> pipeline.ingest(frame(AUTOSEEKER.replace("8168000008", "8168000009")), RECEIVED, session))
                .isInstanceOf(ProtocolException.class).hasMessageContaining("identity");
        verify(publisher).publish(any());
    }

    @Test
    void ingest_unrecognizedFrame_rejectsAndLeavesSessionUnbound() {
        // given
        var session = new ConnectionSession();
        // when / then
        assertThatThrownBy(() -> pipeline.ingest(frame("not a tracker frame#"), RECEIVED, session))
                .isInstanceOf(ProtocolException.class);
        assertThat(session.protocol()).isNull();
        assertThat(session.boundDeviceId()).isNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void ingest_malformedFrameOfDetectedProtocol_doesNotBindSession() {
        // given
        var session = new ConnectionSession();
        // when / then
        assertThatThrownBy(() -> pipeline.ingest(frame(AUTOSEEKER.replace("2234.9273", "NaN")), RECEIVED, session))
                .isInstanceOf(ProtocolException.class);
        assertThat(session.protocol()).isNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void ingest_publisherFails_surfacesAsInfrastructureFailureNotProtocolRejection() {
        // given
        doThrow(new IllegalStateException("synthetic broker outage")).when(publisher).publish(any());
        // when / then: ProtocolException is itself an InfrastructureException, so exclude it explicitly
        assertThatThrownBy(() -> pipeline.ingest(frame(AUTOSEEKER), RECEIVED, new ConnectionSession()))
                .isInstanceOf(InfrastructureException.class).isNotInstanceOf(ProtocolException.class);
    }

    @Test
    void ingest_protocolLeaksUncheckedException_isRejectedWithoutEchoingItsMessage() {
        // given: a buggy plug-in that throws with device payload in the message
        var leaky = new DeviceProtocol() {
            @Override public String name() { return "Leaky"; }
            @Override public FramingSpec framing() { return new FramingSpec.Delimited((byte) '#', 1024); }
            @Override public boolean recognizes(Frame frame) { return true; }
            @Override public List<DeviceMessage> decode(Frame frame, Instant receivedAt) {
                throw new NumberFormatException("For input string: \"UNTRUSTED-MARKER\"");
            }
            @Override public CommandCodec commands() { return null; }
        };
        var registry = new ProtocolRegistry(List.of(leaky));
        var isolated = new IngestPipeline(registry, publisher, Clock.fixed(RECEIVED, ZoneOffset.UTC));
        // when / then
        assertThatThrownBy(() -> isolated.ingest(frame("x#"), RECEIVED, new ConnectionSession()))
                .isInstanceOf(ProtocolException.class).hasMessageNotContaining("UNTRUSTED-MARKER");
    }

    @Test
    void ingest_protocolReturnsMessagesForDifferentDevices_isRejected() {
        // given
        var mixed = new DeviceProtocol() {
            @Override public String name() { return "Mixed"; }
            @Override public FramingSpec framing() { return new FramingSpec.Delimited((byte) '#', 1024); }
            @Override public boolean recognizes(Frame frame) { return true; }
            @Override public List<DeviceMessage> decode(Frame frame, Instant receivedAt) {
                return List.of(new StatusReport("1111111111", receivedAt, DeviceHealth.NONE),
                        new StatusReport("2222222222", receivedAt, DeviceHealth.NONE));
            }
            @Override public CommandCodec commands() { return null; }
        };
        var isolated = new IngestPipeline(new ProtocolRegistry(List.of(mixed)), publisher, Clock.fixed(RECEIVED, ZoneOffset.UTC));
        // when / then
        assertThatThrownBy(() -> isolated.ingest(frame("x#"), RECEIVED, new ConnectionSession()))
                .isInstanceOf(ProtocolException.class);
    }
}
