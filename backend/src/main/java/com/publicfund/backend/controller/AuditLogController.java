package com.publicfund.backend.controller;

import com.publicfund.backend.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@CrossOrigin(origins = "http://localhost:5173")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(
            AuditLogService auditLogService) {

        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<?> getAllAuditLogs() {

        return ResponseEntity.ok(
                auditLogService.getAllAuditLogs()
        );
    }

    @GetMapping("/user")
    public ResponseEntity<?> getLogsByUserEmail(
            @RequestParam String email) {

        return ResponseEntity.ok(
                auditLogService
                        .getLogsByUserEmail(email)
        );
    }

    @GetMapping("/action/{action}")
    public ResponseEntity<?> getLogsByAction(
            @PathVariable String action) {

        return ResponseEntity.ok(
                auditLogService
                        .getLogsByAction(action)
        );
    }
}