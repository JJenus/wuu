package com.surf.tracker.devicecomm.application;

import com.surf.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ParserFactoryTest {

    private ParserFactory parserFactory;
    private AutoseekerProtocolParser autoseekerParser;

    @BeforeEach
    void setUp() {
        autoseekerParser = new AutoseekerProtocolParser();
        List<ITrackerProtocolParser> parsers = List.of(autoseekerParser);
        parserFactory = new ParserFactory(parsers);
    }

    @Test
    void testGetParserForAutoseekerData() {
        String autoseekerData = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
        ITrackerProtocolParser parser = parserFactory.getParser(autoseekerData);
        assertNotNull(parser);
        assertTrue(parser instanceof AutoseekerProtocolParser);
        assertEquals("Autoseeker", parser.getProtocolName());
    }

    @Test
    void testGetParserForUnknownData() {
        String unknownData = "UNKNOWN,FORMAT,DATA";
        assertThrows(ProtocolException.class,
            () -> parserFactory.getParser(unknownData));
    }

    @Test
    void testGetParserForNullData() {
        assertThrows(ProtocolException.class,
            () -> parserFactory.getParser(null));
    }

    @Test
    void testGetParserForEmptyData() {
        assertThrows(ProtocolException.class,
            () -> parserFactory.getParser(""));
    }

    @Test
    void testGetParserByNameNotFound() {
        Optional<ITrackerProtocolParser> parser = parserFactory.getParserByName("UNKNOWN");
        assertFalse(parser.isPresent());
    }

    @Test
    void testGetParserByNameNull() {
        Optional<ITrackerProtocolParser> parser = parserFactory.getParserByName(null);
        assertFalse(parser.isPresent());
    }

    @Test
    void testFactoryWithEmptyParserList() {
        ParserFactory emptyFactory = new ParserFactory(List.of());
        String anyData = "any data";
        assertThrows(ProtocolException.class,
            () -> emptyFactory.getParser(anyData));
    }
}
