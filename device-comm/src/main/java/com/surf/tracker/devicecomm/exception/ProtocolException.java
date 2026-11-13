package com.surf.tracker.devicecomm.exception;

import com.surf.tracker.shared.exception.InfrastructureException;

/**
 * A device frame or command was rejected. Messages must never echo untrusted
 * device payload, because they are logged.
 */
public class ProtocolException extends InfrastructureException {

    public ProtocolException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ProtocolException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static ProtocolException parserNotFound() {
        return new ProtocolException("PROTOCOL_PARSER_NOT_FOUND", "No protocol recognizes the received frame");
    }

    public static ProtocolException parseError(String protocolName, String detail) {
        return new ProtocolException("PROTOCOL_PARSE_ERROR",
                String.format("Failed to parse %s frame: %s", protocolName, detail));
    }

    public static ProtocolException frameTooLarge(int maxLength) {
        return new ProtocolException("PROTOCOL_FRAME_TOO_LARGE",
                "Frame exceeds the maximum length of " + maxLength + " bytes");
    }

    public static ProtocolException unsupportedCommand(String protocolName, String command) {
        return new ProtocolException("PROTOCOL_UNSUPPORTED_COMMAND",
                String.format("%s does not support command %s", protocolName, command));
    }
}
