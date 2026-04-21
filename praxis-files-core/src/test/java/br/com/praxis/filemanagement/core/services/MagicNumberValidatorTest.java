package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicNumberValidatorTest {

    private final FileManagementProperties properties = new FileManagementProperties();
    private final MagicNumberValidator validator = new MagicNumberValidator(properties);

    @Test
    void rejectsFilesTooSmallForValidation() {
        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(new byte[]{0x01, 0x02, 0x03}, "text/plain", "text/plain");

        assertFalse(result.isValid());
        assertEquals("INSUFFICIENT_DATA", result.getReasonCode());
    }

    @Test
    void rejectsDangerousExecutableMagic() {
        byte[] bytes = hex("4D5A900003000000");

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(bytes, "application/x-msdownload", "application/x-msdownload");

        assertFalse(result.isValid());
        assertEquals("DANGEROUS_EXECUTABLE", result.getReasonCode());
    }

    @Test
    void rejectsClientMimeMismatchBeforeSignatureValidation() {
        byte[] pdf = hex("255044462D312E37");

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(pdf, "application/pdf", "text/plain");

        assertFalse(result.isValid());
        assertEquals("MIME_TYPE_MISMATCH", result.getReasonCode());
    }

    @Test
    void rejectsSignatureMismatchWhenMagicDoesNotMatchExpectedMime() {
        byte[] pdf = hex("255044462D312E37");

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(pdf, "image/png", "image/png");

        assertFalse(result.isValid());
        assertEquals("SIGNATURE_MISMATCH", result.getReasonCode());
    }

    @Test
    void acceptsMatchingMagicNumber() {
        byte[] png = hex("89504E470D0A1A0A");

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(png, "image/png", "image/png");

        assertTrue(result.isValid());
        assertEquals("VALID", result.getReasonCode());
    }

    @Test
    void detectsDangerousScriptContentWithoutKnownMagic() {
        byte[] script = "<?php echo 'danger';".getBytes(StandardCharsets.UTF_8);

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(script, "text/plain", "text/plain");

        assertFalse(result.isValid());
        assertEquals("DANGEROUS_SCRIPT", result.getReasonCode());
    }

    @Test
    void allowsUnknownSafeTextContent() {
        byte[] text = "just a harmless note".getBytes(StandardCharsets.UTF_8);

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(text, "text/plain", "text/plain");

        assertTrue(result.isValid());
        assertEquals("NO_MAGIC_REQUIRED", result.getReasonCode());
    }

    @Test
    void detectsMp4MagicAtOffsetFour() {
        byte[] mp4 = hex("000000186674797069736F6D");

        MagicNumberValidator.ValidationResult result =
                validator.validateMagicNumbers(mp4, "video/mp4", "video/mp4");

        assertTrue(result.isValid());
        assertEquals("VALID", result.getReasonCode());
    }

    @Test
    void validateMultipartFileUsesTikaAndReturnsValidationErrorOnReadFailure() {
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", hex("255044462D312E37"));

        MagicNumberValidator.ValidationResult validResult = validator.validateMagicNumber(validPdf);
        assertTrue(validResult.isValid());

        MockMultipartFile failingFile = new MockMultipartFile("file", "broken.bin", "application/octet-stream", new byte[0]) {
            @Override
            public byte[] getBytes() {
                throw new RuntimeException("read failed");
            }
        };

        MagicNumberValidator.ValidationResult failingResult = validator.validateMagicNumber(failingFile);
        assertFalse(failingResult.isValid());
        assertEquals("VALIDATION_ERROR", failingResult.getReasonCode());
    }

    @Test
    void validationResultRequiresNonNullMessageAndReasonCode() {
        NullPointerException missingMessage = assertThrows(
                NullPointerException.class,
                () -> new MagicNumberValidator.ValidationResult(true, null, "OK"));
        NullPointerException missingReason = assertThrows(
                NullPointerException.class,
                () -> new MagicNumberValidator.ValidationResult(true, "ok", null));

        assertTrue(missingMessage.getMessage().contains("Message cannot be null"));
        assertTrue(missingReason.getMessage().contains("Reason code cannot be null"));
    }

    private static byte[] hex(String value) {
        byte[] data = new byte[value.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int index = i * 2;
            data[i] = (byte) Integer.parseInt(value.substring(index, index + 2), 16);
        }
        return data;
    }
}
