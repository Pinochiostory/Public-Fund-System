package com.publicfund.backend.config;

import com.publicfund.backend.entity.User;
import com.publicfund.backend.repository.UserRepository;
import com.publicfund.backend.security.AccountRoles;
import com.publicfund.backend.service.AuthService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InitialAdminSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminSeeder.class);
    private final UserRepository users;
    private final AuthService auth;
    private final String fullName;
    private final String email;
    private final String password;

    public InitialAdminSeeder(UserRepository users, AuthService auth,
            @Value("${app.bootstrap-admin.full-name:}") String fullName,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.auth = auth;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (fullName.isBlank() && email.isBlank() && password.isBlank()) {
            log.info("Initial admin bootstrap not configured; no account will be seeded.");
            return;
        }
        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Admin bootstrap requires PUBLIC_FUND_ADMIN_NAME, PUBLIC_FUND_ADMIN_EMAIL and PUBLIC_FUND_ADMIN_PASSWORD.");
        }
        User existing = users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (existing != null) {
            if (!AccountRoles.isAdmin(existing.getRole())) {
                throw new IllegalStateException("Admin bootstrap email belongs to a non-admin account. Choose a different email; existing accounts will not be promoted.");
            }
            log.info("Initial admin already exists; account details left unchanged.");
            return;
        }
        auth.createStaffUser(fullName, email, password, "ADMIN");
        log.info("Initial admin created in the users table.");
    }
}
