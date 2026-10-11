package com.publicfund.backend.controller;

import com.publicfund.backend.dto.ManagedUserResponse;
import com.publicfund.backend.service.UserManagementService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {
    private final UserManagementService service;

    public UserManagementController(UserManagementService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ManagedUserResponse>> list(Principal actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listUsers(actor.getName()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ManagedUserResponse> status(Principal actor, @PathVariable Long id,
            @RequestBody StatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.setStatus(actor.getName(), id, request.status()));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<UserManagementService.TemporaryPasswordResponse> reset(Principal actor, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.resetPassword(actor.getName(), id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }

    public record StatusRequest(String status) {}
}
