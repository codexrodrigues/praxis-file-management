package br.com.praxis.filemanagement.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitExceededExceptionTest {

    @Test
    void preservesMessage() {
        RateLimitExceededException exception = new RateLimitExceededException("rate limit exceeded");
        assertEquals("rate limit exceeded", exception.getMessage());
    }
}
