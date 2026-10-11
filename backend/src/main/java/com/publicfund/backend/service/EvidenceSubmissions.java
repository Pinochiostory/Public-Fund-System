package com.publicfund.backend.service;

import com.publicfund.backend.entity.PhaseEvidence;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Groups files without changing historic single-file evidence records. */
public final class EvidenceSubmissions {
    private EvidenceSubmissions() {}
    public static List<PhaseEvidence> latest(List<PhaseEvidence> history) {
        var newest = history.stream().max(Comparator.comparing(PhaseEvidence::getId,
                Comparator.nullsFirst(Long::compareTo))).orElse(null);
        if (newest == null) return List.of();
        if (newest.getSubmissionId() == null) return List.of(newest);
        return history.stream().filter(e -> Objects.equals(e.getSubmissionId(), newest.getSubmissionId())).toList();
    }
    public static boolean fullyApproved(List<PhaseEvidence> history) {
        var current = latest(history);
        return !current.isEmpty() && history.stream().noneMatch(e -> "PENDING".equals(e.getStatus()))
                && current.stream().allMatch(e -> "APPROVED".equals(e.getStatus()));
    }
}
