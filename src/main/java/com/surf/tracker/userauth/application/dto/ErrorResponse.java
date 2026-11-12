package com.surf.tracker.userauth.application.dto;

import java.time.Instant;
import java.util.Map;

@io.swagger.v3.oas.annotations.media.Schema(description = "MVC service/validation error. Security filters may instead return only code. timestamp currently defaults to the epoch; it is not a reliable occurrence time.")
public class ErrorResponse {
    private String code;
    private String message;
    private Instant timestamp = Instant.EPOCH;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Optional validation messages keyed by the rejected Java field name; this is a dynamic field-error boundary.")
    private Map<String, String> fieldErrors;

    public ErrorResponse() {}

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(Map<String, String> fieldErrors) { this.fieldErrors = fieldErrors; }
}
