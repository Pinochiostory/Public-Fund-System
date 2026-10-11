package com.publicfund.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import com.publicfund.backend.service.WorkflowLocks.Conflict;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    public static ResponseEntity<?> response(RuntimeException e) {
        if (e instanceof org.springframework.web.server.ResponseStatusException status && status.getStatusCode().is4xxClientError())
            return ResponseEntity.status(status.getStatusCode()).body(Map.of("message", status.getReason()==null ? "The requested record is unavailable." : status.getReason()));
        if (e instanceof AccessDeniedException)
            return ResponseEntity.status(403).body(Map.of("message", "You do not have permission to perform this action."));
        if (e instanceof Conflict || e instanceof org.springframework.orm.ObjectOptimisticLockingFailureException
                || e instanceof org.springframework.dao.CannotAcquireLockException)
            return ResponseEntity.status(409).body(Map.of("message", "This record was updated by another user. Refresh it before performing another action."));
        if (e instanceof org.springframework.dao.DataIntegrityViolationException)
            return ResponseEntity.status(409).body(Map.of("message", "This action conflicts with an existing record. Refresh before trying again."));
        if (e instanceof IllegalArgumentException && !(e instanceof DataAccessException))
            return ResponseEntity.badRequest().body(e.getMessage());
        return ResponseEntity.internalServerError().body(Map.of("message", "The operation could not be saved. Please try again; if the problem persists, contact the system owner."));
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handle(RuntimeException e) { return response(e); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> largeFile() {
        return ResponseEntity.status(413).body(Map.of("message", "Each file must be less than 25 MB and the complete submission less than 100 MB."));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidInput(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "A required field is missing or has an invalid value. Check the form and try again."));
    }
}
