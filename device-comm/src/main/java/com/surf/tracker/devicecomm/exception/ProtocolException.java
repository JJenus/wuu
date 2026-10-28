package com.surf.tracker.devicecomm.exception;

import com.surf.tracker.shared.exception.InfrastructureException;

public class ProtocolException extends InfrastructureException {

    public ProtocolException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ProtocolException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static ProtocolException parserNotFound(String data) {

        return new ProtocolException(
            "PROTOCOL_PARSER_NOT_FOUND",
            String.format("No parser found for the provided data%s", data)
        );
    }

    public static ProtocolException invalidHeader(String protocolName) {
        return new ProtocolException(
            "PROTOCOL_INVALID_HEADER",
            String.format("Invalid header for %s protocol", protocolName)
        );
    }

    public static ProtocolException parseError(String protocolName, String detail) {
        return new ProtocolException(
            "PROTOCOL_PARSE_ERROR",
            String.format("Failed to parse %s data: %s", protocolName, detail)
        );
    }

    public static ProtocolException unsupportedProtocol(int protocolNumber) {
        return new ProtocolException(
            "PROTOCOL_UNSUPPORTED",
            String.format("Unsupported protocol number: 0x%02x", protocolNumber)
        );
    }

    public static ProtocolException commandBuildError(String protocolName, String commandType) {
        return new ProtocolException(
            "PROTOCOL_COMMAND_BUILD_ERROR",
            String.format("Failed to build %s command for %s protocol", commandType, protocolName)
        );
    }
}
