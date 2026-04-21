package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStructureValidatorTest {

    private FileStructureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileStructureValidator();
        ReflectionTestUtils.setField(validator, "properties", new FileManagementProperties());
    }

    @Test
    void rejectsEmptyInput() {
        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(new byte[0], "application/pdf", "empty.pdf");

        assertFalse(result.isValid());
        assertEquals("EMPTY_FILE", result.getReasonCode());
    }

    @Test
    void returnsValidForUnsupportedMimeType() {
        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure("hello".getBytes(StandardCharsets.UTF_8), "text/plain", "doc.txt");

        assertTrue(result.isValid());
        assertEquals("VALID", result.getReasonCode());
    }

    @Test
    void rejectsZipWithPathTraversalEntry() throws Exception {
        byte[] zip = createZip("../evil.txt", "boom");

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(zip, "application/zip", "archive.zip");

        assertFalse(result.isValid());
        assertEquals("PATH_TRAVERSAL", result.getReasonCode());
    }

    @Test
    void rejectsZipWithSuspiciousExecutableContent() throws Exception {
        byte[] zip = createZip("payload.exe", "MZ");

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(zip, "application/zip", "archive.zip");

        assertFalse(result.isValid());
        assertEquals("EXECUTABLE_CONTENT", result.getReasonCode());
    }

    @Test
    void rejectsEmptyZipArchive() throws Exception {
        byte[] zip = createEmptyZip();

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(zip, "application/zip", "archive.zip");

        assertFalse(result.isValid());
        assertEquals("EMPTY_ARCHIVE", result.getReasonCode());
    }

    @Test
    void validatesCleanZipArchive() throws Exception {
        byte[] zip = createZip("folder/readme.txt", "hello");

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(zip, "application/zip", "archive.zip");

        assertTrue(result.isValid());
    }

    @Test
    void rejectsZipWithNestedArchive() throws Exception {
        byte[] zip = createZip("nested/archive.zip", "boom");

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(zip, "application/zip", "archive.zip");

        assertFalse(result.isValid());
        assertEquals("NESTED_ARCHIVE", result.getReasonCode());
    }

    @Test
    void rejectsCorruptedZip() {
        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure("not-a-zip".getBytes(StandardCharsets.UTF_8), "application/zip", "broken.zip");

        assertFalse(result.isValid());
        assertEquals("EMPTY_ARCHIVE", result.getReasonCode());
    }

    @Test
    void rejectsPdfWithInvalidHeader() {
        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure("NOTPDF".getBytes(StandardCharsets.UTF_8), "application/pdf", "bad.pdf");

        assertFalse(result.isValid());
        assertEquals("TRUNCATED_PDF", result.getReasonCode());
    }

    @Test
    void rejectsPdfWithSuspiciousJavascript() {
        byte[] pdf = ("%PDF-1.7\n1 0 obj\n/JavaScript /JS (eval(alert('x')))\n%%EOF")
            .getBytes(StandardCharsets.UTF_8);

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(pdf, "application/pdf", "scripted.pdf");

        assertFalse(result.isValid());
        assertEquals("SUSPICIOUS_JAVASCRIPT", result.getReasonCode());
    }

    @Test
    void rejectsPdfWithInvalidVersion() {
        byte[] pdf = ("%PDF-9.9\n1 0 obj\n<< /Type /Catalog >>\n%%EOF")
            .getBytes(StandardCharsets.UTF_8);

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(pdf, "application/pdf", "invalid-version.pdf");

        assertFalse(result.isValid());
        assertEquals("INVALID_PDF_VERSION", result.getReasonCode());
    }

    @Test
    void rejectsPdfWithSuspiciousAction() {
        byte[] pdf = ("%PDF-1.7\n1 0 obj\n/Launch /URI\n%%EOF")
            .getBytes(StandardCharsets.UTF_8);

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(pdf, "application/pdf", "action.pdf");

        assertFalse(result.isValid());
        assertEquals("SUSPICIOUS_ACTIONS", result.getReasonCode());
    }

    @Test
    void rejectsPdfMissingEof() {
        byte[] pdf = ("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\n")
            .getBytes(StandardCharsets.UTF_8);

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(pdf, "application/pdf", "missing-eof.pdf");

        assertFalse(result.isValid());
        assertEquals("MISSING_EOF", result.getReasonCode());
    }

    @Test
    void rejectsPdfWithEmbeddedFiles() {
        byte[] pdf = ("%PDF-1.7\n1 0 obj\n/EmbeddedFile /Filespec\n%%EOF")
            .getBytes(StandardCharsets.UTF_8);

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(pdf, "application/pdf", "embedded.pdf");

        assertFalse(result.isValid());
        assertEquals("EMBEDDED_FILES", result.getReasonCode());
    }

    @Test
    void validatesCleanPdf() {
        byte[] pdf = ("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\n%%EOF")
            .getBytes(StandardCharsets.UTF_8);

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(pdf, "application/pdf", "clean.pdf");

        assertTrue(result.isValid());
    }

    @Test
    void rejectsJarWithoutManifest() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("com/example/App.class"));
            jar.write(new byte[]{0x1, 0x2});
            jar.closeEntry();
        }

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(out.toByteArray(), "application/java-archive", "app.jar");

        assertFalse(result.isValid());
        assertEquals("MISSING_MANIFEST", result.getReasonCode());
    }

    @Test
    void rejectsJarWithSuspiciousNativeLibrary() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jar.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("native.dll"));
            jar.write(new byte[]{0x1});
            jar.closeEntry();
        }

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(out.toByteArray(), "application/java-archive", "app.jar");

        assertFalse(result.isValid());
        assertEquals("SUSPICIOUS_JAR_CONTENT", result.getReasonCode());
    }

    @Test
    void validatesCleanJarWithManifestAndClass() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jar.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("com/example/App.class"));
            jar.write(new byte[]{0x1, 0x2});
            jar.closeEntry();
        }

        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure(out.toByteArray(), "application/java-archive", "app.jar");

        assertTrue(result.isValid());
        assertEquals("VALID", result.getReasonCode());
    }

    @Test
    void rejectsCorruptedJar() {
        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure("not-a-jar".getBytes(StandardCharsets.UTF_8), "application/java-archive", "broken.jar");

        assertFalse(result.isValid());
        assertEquals("EMPTY_ARCHIVE", result.getReasonCode());
    }

    @Test
    void returnsValidationErrorWhenMimeTypeIsNull() {
        FileStructureValidator.ValidationResult result =
            validator.validateFileStructure("hello".getBytes(StandardCharsets.UTF_8), null, "unknown.bin");

        assertFalse(result.isValid());
        assertEquals("VALIDATION_ERROR", result.getReasonCode());
    }

    private byte[] createZip(String entryName, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private byte[] createEmptyZip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream ignored = new ZipOutputStream(out)) {
        }
        return out.toByteArray();
    }
}
