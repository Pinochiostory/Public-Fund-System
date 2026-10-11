package com.publicfund.backend.service;

import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageSafetyTests {
    @TempDir Path directory;
    FileStorageService storage() { return new FileStorageService(directory.toString()); }
    MockMultipartFile pdf() { return new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes()); }

    @Test void validatesContentsRatherThanTrustingMimeHeader() {
        assertThrows(IllegalArgumentException.class,()->storage().validate(
                new MockMultipartFile("file","fake.pdf","application/pdf","<script>not a pdf</script>".getBytes()),false));
        assertThrows(IllegalArgumentException.class,()->storage().validate(
                new MockMultipartFile("file","fake.png","image/png","%PDF-1.4".getBytes()),false));
    }
    @Test void rejectsAtSizeLimit() {
        MultipartFile file=mock(MultipartFile.class);when(file.getSize()).thenReturn(25L*1024*1024);
        assertTrue(assertThrows(IllegalArgumentException.class,()->storage().validate(file,false)).getMessage().contains("less than 25 MB"));
    }
    @Test void paymentProofCannotBeDocx() {
        assertTrue(assertThrows(IllegalArgumentException.class,()->storage().validate(
                new MockMultipartFile("file","proof.docx","application/zip",new byte[]{80,75,3,4}),false)).getMessage().contains("Only PDF, JPG and PNG"));
    }
    @Test void rejectsArchivesThatAreNotDocx() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("unrelated.txt"));zip.write("text".getBytes());zip.closeEntry();}
        assertThrows(IllegalArgumentException.class,()->storage().validate(
                new MockMultipartFile("file","proof.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes.toByteArray()),true));
    }
    @Test void successfulStorageUsesGeneratedNameAndCanBeRetrieved() throws Exception {
        var storage=storage();String saved=storage.storePaymentProof(pdf());
        assertTrue(Path.of(saved).startsWith(directory));
        assertNotEquals("proof.pdf",Path.of(saved).getFileName().toString());
        try (InputStream input=storage.read(saved).getInputStream()) { assertArrayEquals(pdf().getBytes(),input.readAllBytes()); }
    }
    @Test void storageFailureDoesNotReportSuccess() throws Exception {
        Path blocked=Files.createFile(directory.resolve("blocked"));
        var storage=new FileStorageService(blocked.toString());
        assertEquals("Upload failed. Your file was not stored; please try again.",
                assertThrows(IllegalArgumentException.class,()->storage.storePaymentProof(pdf())).getMessage());
    }
    @Test void rollbackRemovesOnlyTheNewUncommittedFile() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            String saved=storage().storeEvidenceFile(pdf());assertTrue(Files.exists(Path.of(saved)));
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(Files.exists(Path.of(saved)));
        }finally{TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void downloadCannotEscapeStorageRoot() {
        assertThrows(IllegalArgumentException.class,()->storage().read(directory.resolve("../not-authorized").toString()));
    }
}
