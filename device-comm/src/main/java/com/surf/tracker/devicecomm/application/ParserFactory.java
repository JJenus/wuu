package com.surf.tracker.devicecomm.application;

import com.surf.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

        log.debug("getParser() called with data (length: {}): {}",
                rawData.length(),
                rawData);

        log.debug("Total parsers available: {}", parsers.size());
        parsers.forEach(parser ->
                log.debug("Available parser: {}", parser.getProtocolName()));

        // AtomicBoolean to track if we found any parser
        final AtomicBoolean parserFound = new AtomicBoolean(false);
        final AtomicReference<String> selectedParserName = new AtomicReference<>("");

        ITrackerProtocolParser selectedParser = parsers.stream()
                .filter(parser -> {
                    boolean canParse = parser.canParse(rawData);
                    String status = canParse ? "PASS" : "FAIL";
                    log.debug("Testing parser '{}': {} for data: {}...",
                            parser.getProtocolName(),
                            status,
                            rawData);

                    if (canParse && !parserFound.get()) {
                        parserFound.set(true);
                        selectedParserName.set(parser.getProtocolName());
                    }

                    return canParse;
                })
                .findFirst()
                .orElseGet(() -> {
                    // This block executes if no parser is found
                    log.debug("No parser successfully parsed the data. Testing complete.");

                    // Log detailed failure analysis
                    log.error("Failed to find parser for data ({} chars): {}",
                            rawData.length(),
                            rawData.substring(0, Math.min(rawData.length(), 150)));

                    // Check if data has expected format
                    if (!rawData.startsWith("*")) {
                        log.error("Data doesn't start with '*'");
                    }
                    if (!rawData.endsWith("#")) {
                        log.error("Data doesn't end with '#'");
                    }

                    String[] parts = rawData.substring(1, rawData.length() - 1).split(",");
                    log.error("Data parts ({}): {}", parts.length, String.join("|", parts));

                    throw ProtocolException.parserNotFound(rawData);
                });

        if (selectedParser != null) {
            log.info("Selected parser '{}' for data: {}",
                    selectedParser.getProtocolName(),
                    rawData);

            log.debug("Parser selection successful for protocol: {}",
                    selectedParser.getProtocolName());
        }

        return selectedParser;
    }

    public Optional<ITrackerProtocolParser> getParserByName(String protocolName) {
        return parsers.stream()
            .filter(parser -> parser.getProtocolName().equalsIgnoreCase(protocolName))
            .findFirst();
    }
}
