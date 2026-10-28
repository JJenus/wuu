package com.surf.tracker.shared.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainExceptionTest {

    @Test
    void testDomainExceptionCreation() {
        DomainException exception = new ValidationException("TEST_CODE", "Test message");

        assertEquals("TEST_CODE", exception.getErrorCode());
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testDomainExceptionWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        DomainException exception = new ValidationException("TEST_CODE", "Test message", cause);

        assertEquals("TEST_CODE", exception.getErrorCode());
        assertEquals("Test message", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testBusinessRuleException() {
        BusinessRuleException exception = new BusinessRuleException("BUSINESS_ERROR", "Business rule violated");

        assertEquals("BUSINESS_ERROR", exception.getErrorCode());
        assertEquals("Business rule violated", exception.getMessage());
        assertTrue(exception instanceof DomainException);
    }

    @Test
    void testValidationException() {
        ValidationException exception = new ValidationException("VALIDATION_ERROR", "Invalid input");

        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals("Invalid input", exception.getMessage());
        assertTrue(exception instanceof DomainException);
    }

    @Test
    void testInfrastructureException() {
        InfrastructureException exception = new InfrastructureException("INFRA_ERROR", "Infrastructure failure");

        assertEquals("INFRA_ERROR", exception.getErrorCode());
        assertEquals("Infrastructure failure", exception.getMessage());
        assertTrue(exception instanceof DomainException);
    }
}
