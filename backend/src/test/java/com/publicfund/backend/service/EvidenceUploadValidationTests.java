package com.publicfund.backend.service;

import com.publicfund.backend.entity.Project;
import com.publicfund.backend.entity.ProjectPhase;
import com.publicfund.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceUploadValidationTests {
    private final PhaseEvidenceRepository evidence = mock(PhaseEvidenceRepository.class);
    private final ProjectPhaseRepository phases = mock(ProjectPhaseRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final WorkflowLocks locks=mock(WorkflowLocks.class);
    private final PhaseEvidenceService service = new PhaseEvidenceService(mock(UserRepository.class), locks,
        evidence, phases, projects, storage, mock(AuditLogService.class), mock(NotificationService.class));
    private void setup() {
        ProjectPhase phase = new ProjectPhase();
        phase.setProjectId(1L);
        phase.setStatus("IN_PROGRESS");
        when(locks.phase(11L)).thenReturn(phase);
        Project project = new Project();
        project.setApplicantId(42L);
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(evidence.findByPhaseIdAndStatus(11L, "PENDING")).thenReturn(List.of());
    }
    @Test void rejectsFileAtLimitBeforeStorage() {
        setup();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(25L * 1024 * 1024);
        var failure = assertThrows(RuntimeException.class, () -> service.uploadEvidence(11L, 42L, file));
        assertTrue(failure.getMessage().contains("less than 25 MB"));
        verifyNoInteractions(storage);
    }
    @Test void rejectsUploadingToAnotherProvidersProject() {
        setup();
        var failure = assertThrows(RuntimeException.class, () -> service.uploadEvidence(11L, 43L, mock(MultipartFile.class)));
        assertTrue(failure.getMessage().contains("own project"));
        verifyNoInteractions(storage);
    }
}
