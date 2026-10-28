package com.surf.tracker.devicecomm.infrastructure;

import com.surf.tracker.devicecomm.exception.ProtocolParseException;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoseekerProtocolParserTest {

    private AutoseekerProtocolParser parser;

    @BeforeEach
    void setUp() {
        parser = new AutoseekerProtocolParser();
    }

    @Test
    void testCanParseValidAutoseekerData() {
        String data = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        assertTrue(parser.canParse(data));
    }

    @Test
    void testCanParseInvalidHeader() {
        String data = "*GPS,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF#";
        assertFalse(parser.canParse(data));
    }

    @Test
    void testCanParseNullData() {
        assertFalse(parser.canParse(null));
    }

    @Test
    void testCanParseEmptyData() {
        assertFalse(parser.canParse(""));
    }

    @Test
    void testParseValidAutoseekerData() throws ProtocolParseException {
        String data = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        LocationPoint location = parser.parse(data);
        assertNotNull(location);
        assertEquals(22.5821, location.latitude(), 0.0001);
        assertEquals(113.9066, location.longitude(), 0.0001);
    }

    @Test
    void testParseInvalidHeader() {
        String data = "*GPS,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715#";
        assertFalse(parser.canParse(data));
    }

    @Test
    void testParseIncompleteData() {
        String data = "*HQ,8168000008,V1";
        assertFalse(parser.canParse(data));
    }

    @Test
    void testParseMalformedNumber() {
        String data = "*XYZ,1234567890,V9,invalid,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        assertFalse(parser.canParse(data));
    }

    @Test
    void testBuildFuelCutCommand() {
        String deviceId = "8168000008";
        String command = parser.buildFuelCutCommand(deviceId);
        assertNotNull(command);
        assertTrue(command.startsWith("*HQ," + deviceId + ",S20,"));
        assertTrue(command.endsWith("#"));
    }

    @Test
    void testBuildEngineOnCommand() {
        String deviceId = "8168000008";
        String command = parser.buildEngineOnCommand(deviceId);
        assertNotNull(command);
        assertTrue(command.startsWith("*HQ," + deviceId + ",S20,"));
        assertTrue(command.endsWith("#"));
    }

    @Test
    void testGetProtocolName() {
        assertEquals("Autoseeker", parser.getProtocolName());
    }

    @Test
    void testParseWithDifferentFormats() throws ProtocolParseException {
        String data = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,100.50,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        LocationPoint location = parser.parse(data);
        assertNotNull(location);
        assertEquals(22.5821, location.latitude(), 0.001);
        assertEquals(113.9066, location.longitude(), 0.001);
    }

    @Test
    void testParseZeroSpeed() throws ProtocolParseException {
        String data = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.00,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        LocationPoint location = parser.parse(data);
        assertNotNull(location);
        assertEquals(0.0f, location.speedKmh(), 0.1f);
    }

    @Test
    void testParseNegativeSpeed() throws ProtocolParseException {
        String data = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        LocationPoint location = parser.parse(data);
        assertNotNull(location);
    }
}
