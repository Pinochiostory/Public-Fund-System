package com.publicfund.backend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/test")
    public String test(Authentication authentication) {

        return "Authenticated as: "
                + authentication.getName()
                + " | Role: "
                + authentication.getAuthorities();
    }

    @GetMapping("/api/test/admin")
    public String adminTest() {

        return "Administrator access granted.";
    }

    @GetMapping("/api/test/provider")
    public String providerTest() {

        return "Service Provider access granted.";
    }

    @GetMapping("/api/test/officer")
    public String officerTest() {

        return "Verification Officer access granted.";
    }
}