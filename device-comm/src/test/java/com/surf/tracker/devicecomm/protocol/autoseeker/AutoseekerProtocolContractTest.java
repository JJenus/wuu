package com.surf.tracker.devicecomm.protocol.autoseeker;

import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.DeviceProtocolContractTest;

import java.util.List;

class AutoseekerProtocolContractTest extends DeviceProtocolContractTest {
    @Override
    protected DeviceProtocol protocol() {
        return new AutoseekerProtocol();
    }

    @Override
    protected List<String> validFrames() {
        return List.of(
                "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#",
                "*HQ,8168000008,V4,S20,DONE,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715F7FFBBFF,460,00,10342,3721#");
    }
}
