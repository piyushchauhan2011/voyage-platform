package com.voyage.app.token;

import com.voyage.app.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Creates a new refresh token for the user, revoking all existing ones first.
     * One active refresh token per user keeps revocation simple.
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        revokeAllUserTokens(user);
        RefreshToken token = new RefreshToken(
                UUID.randomUUID().toString(),
                user,
                Instant.now().plusMillis(refreshExpiration)
        );
        return refreshTokenRepository.save(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked() || token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new RefreshTokenException("Refresh token has expired or been revoked — please log in again");
        }
        return token;
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
