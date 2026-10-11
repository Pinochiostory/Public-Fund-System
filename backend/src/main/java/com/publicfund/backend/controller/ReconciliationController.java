package com.publicfund.backend.controller;

import com.publicfund.backend.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/projects/{projectId}/reconciliation")
public class ReconciliationController {
    private final ReconciliationService service;
    private final FileStorageService files;
    public ReconciliationController(ReconciliationService service,FileStorageService files) {this.service=service;this.files=files;}

    @GetMapping public Object summary(@PathVariable Long projectId) { return service.summary(projectId); }

    @PostMapping("/declarations")
    public Object declare(@PathVariable Long projectId,@RequestParam BigDecimal amount,@RequestParam String note,
            @RequestParam MultipartFile file,@RequestHeader(value="If-Match",required=false) String version) {
        return service.declare(projectId,amount,note,file,version);
    }
    @PostMapping("/returns")
    public Object returned(@PathVariable Long projectId,@RequestParam Long declarationId,@RequestParam BigDecimal amount,
            @RequestParam String note,@RequestParam String reference,@RequestParam LocalDate transferredOn,
            @RequestParam String destinationVersion,@RequestParam MultipartFile file,
            @RequestHeader(value="If-Match",required=false) String version) {
        return service.submitReturn(projectId,declarationId,amount,note,reference,transferredOn,destinationVersion,file,version);
    }
    @PostMapping("/{id}/review")
    public Object review(@PathVariable Long projectId,@PathVariable Long id,@RequestParam boolean approve,
            @RequestParam String reason,@RequestParam(required=false) String receiptReference,
            @RequestHeader(value="If-Match",required=false) String version) {
        return service.review(projectId,id,approve,reason,receiptReference,version);
    }
    @GetMapping("/{id}/file")
    public ResponseEntity<?> download(@PathVariable Long projectId,@PathVariable Long id) {
        var record=service.downloadRecord(projectId,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(record.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(record.getFileName(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .body(files.read(record.getFilePath()));
    }
}

