package com.surf.tracker.devicecomm.application;

import com.surf.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ParserFactory {
    private static final Logger log = LoggerFactory.getLogger(ParserFactory.class);
    private final List<ITrackerProtocolParser> parsers;

    public ParserFactory(List<ITrackerProtocolParser> parsers) {
        this.parsers = parsers;
    }

    public ITrackerProtocolParser getParser(String rawData) {
        if (rawData == null || rawData.trim().isEmpty()) {
            log.error("Received null or empty raw data");
            throw ProtocolException.parserNotFound("null or empty data");
        }

        log.debug("Selecting protocol parser for {} characters", rawData.length());
        ITrackerProtocolParser selectedParser = parsers.stream()
                .filter(parser -> parser.canParse(rawData))
                .findFirst()
                .orElseThrow(() -> ProtocolException.parserNotFound(" (unrecognized frame)"));
        log.debug("Selected protocol parser '{}'", selectedParser.getProtocolName());
        return selectedParser;
    }

    public Optional<ITrackerProtocolParser> getParserByName(String protocolName) {
        return parsers.stream()
            .filter(parser -> parser.getProtocolName().equalsIgnoreCase(protocolName))
            .findFirst();
    }
}
