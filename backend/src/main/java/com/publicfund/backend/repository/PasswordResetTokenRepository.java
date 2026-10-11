package com.publicfund.backend.repository;

import com.publicfund.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(
            String token
    );

    @org.springframework.data.jpa.repository.Query("select t.userId from PasswordResetToken t where t.token = :token")
    Optional<Long> findUserIdByToken(@org.springframework.data.repository.query.Param("token") String token);

    void deleteByUserId(
            Long userId
    );
}
