package com.surf.tracker.devicecomm.application;

import com.surf.tracker.devicecomm.domain.DeviceDataPacket;
import com.surf.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.util.TimeProvider;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.exception.DeviceException;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class DeviceDataProcessor {
    private final ParserFactory parserFactory;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public DeviceDataProcessor(ParserFactory parserFactory, EventPublisher eventPublisher, Clock clock) {
        this.parserFactory = parserFactory;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void processDeviceData(DeviceDataPacket packet) {
        try {
            ITrackerProtocolParser parser = parserFactory.getParser(packet.rawData());
            LocationPoint location = parser.parse(packet.rawData());

            LocationDataEvent event = new LocationDataEvent(
                clock,
                TimeProvider.newUuid(),
                packet.deviceId(),
                location,
                parser.getProtocolName(),
                null
            );

            eventPublisher.publish(event);

        } catch (ProtocolException | DeviceException e) {
            throw e;
        } catch (Exception e) {
            throw new com.surf.tracker.shared.exception.InfrastructureException(
                "DEVICE_DATA_PROCESS_ERROR",
                "Failed to process device data for " + packet.deviceId(),
                e
            );
        }
    }
}