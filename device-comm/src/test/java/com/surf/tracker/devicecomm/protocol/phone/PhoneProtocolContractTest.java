package com.surf.tracker.devicecomm.protocol.phone;

import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.DeviceProtocolContractTest;
import com.surf.tracker.devicecomm.protocol.Frame;

import java.nio.charset.StandardCharsets;
import java.util.List;

class PhoneProtocolContractTest extends DeviceProtocolContractTest {
    @Override
    protected DeviceProtocol protocol() {
        return new PhoneProtocol(100);
    }

    @Override
    protected Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.UTF_8), "2348000000001");
    }

    @Override
    protected List<String> validFrames() {
        // 2026-11-13T00:00:00Z is the contract test's receive time.
        return List.of("{\"lat\":6.5244,\"lng\":3.3792,\"speed_kmh\":12.5,\"accuracy_m\":8,\"ts\":1794528000000}",
                "{\"lat\":6.5244,\"lng\":3.3792,\"speed_kmh\":0,\"ts\":1794527990000,\"battery_pct\":80}");
    }
}
