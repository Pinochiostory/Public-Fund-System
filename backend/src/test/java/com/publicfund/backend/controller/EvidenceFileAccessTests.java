package com.publicfund.backend.controller;

import com.publicfund.backend.entity.PhaseEvidence;
import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.service.PhaseEvidenceService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceFileAccessTests {
    private final PhaseEvidenceService service = mock(PhaseEvidenceService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PhaseEvidenceController controller = new PhaseEvidenceController(service, users);

    private void evidence() {
        PhaseEvidence file = new PhaseEvidence();
        file.setUploadedBy(42L);
        file.setFileType("application/pdf");
        file.setFileName("evidence.pdf");
        when(service.getEvidenceById(7L)).thenReturn(file);
        when(service.getEvidenceFile(7L)).thenReturn(new ByteArrayResource(new byte[]{1,2,3}));
    }
    private UsernamePasswordAuthenticationToken provider(long id) {
        User user = new User();
        user.setId(id);
        when(users.findByEmail("provider@example.com")).thenReturn(Optional.of(user));
        return new UsernamePasswordAuthenticationToken("provider@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE_PROVIDER")));
    }
    @Test void ownerCanDownloadOwnEvidence() {
        evidence();
        assertEquals(200, controller.getEvidenceFile(7L, provider(42L)).getStatusCode().value());
        verify(service).getEvidenceFile(7L);
    }
    @Test void anotherProviderCannotDownloadEvidence() {
        evidence();
        assertEquals(404, controller.getEvidenceFile(7L, provider(43L)).getStatusCode().value());
        verify(service, never()).getEvidenceFile(anyLong());
    }
    @Test void officerAccessStillWorksWithoutDatabaseUser() {
        evidence();
        var officer = new UsernamePasswordAuthenticationToken("officer@publicfund.gov.za", null,
                List.of(new SimpleGrantedAuthority("ROLE_VERIFICATION_OFFICER")));
        assertEquals(200, controller.getEvidenceFile(7L, officer).getStatusCode().value());
        verifyNoInteractions(users);
    }
    @Test void unauthenticatedAccessDoesNotReadFile() {
        evidence();
        assertEquals(404, controller.getEvidenceFile(7L, null).getStatusCode().value());
        verify(service, never()).getEvidenceFile(anyLong());
    }
}
