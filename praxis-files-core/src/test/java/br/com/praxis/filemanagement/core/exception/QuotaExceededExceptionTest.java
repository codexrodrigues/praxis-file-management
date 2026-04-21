package br.com.praxis.filemanagement.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuotaExceededExceptionTest {

    @Test
    void preservesMessage() {
        QuotaExceededException exception = new QuotaExceededException("quota exceeded");
        assertEquals("quota exceeded", exception.getMessage());
    }
}
