package com.surf.tracker.devicecomm.protocol.gt06;

import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.DeviceProtocolContractTest;

import java.util.List;

class Gt06ProtocolContractTest extends DeviceProtocolContractTest {
    @Override
    protected DeviceProtocol protocol() {
        return new Gt06Protocol();
    }

    @Override
    protected List<String> validFrames() {
        return List.of(
                "*HQ,865205030330012,V1,145452,A,2240.55181,N,11358.32389,E,0.00,0,100815,FFFFFBFF#",
                "*HQ,865205030330012,V0#",
                "*HQ,865205030330012,HTBT#",
                "*HQ,865205030330012,V3,000201,46000,07,0000,0001,0F00,100815,FFFFFBFF#");
    }
}
