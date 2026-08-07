package com.voyage.app.auth;

import com.voyage.app.exception.ConflictException;
import com.voyage.app.exception.UnauthorizedException;
import com.voyage.app.security.JwtService;
import com.voyage.app.token.RefreshToken;
import com.voyage.app.token.RefreshTokenService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        // BCrypt hashes the password — the plaintext is never stored
        User user = new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            // authenticate() loads UserDetails via UserDetailsService, then verifies the password
            // with BCryptPasswordEncoder — throws BadCredentialsException if wrong
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid username or password");
        }
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
        refreshTokenService.verifyExpiration(refreshToken);
        // Issue a new access token; the refresh token remains valid until its own expiry
        String accessToken = jwtService.generateToken(refreshToken.getUser());
        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", jwtExpiration);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.findByToken(request.refreshToken())
                .ifPresent(token -> refreshTokenService.revokeAllUserTokens(token.getUser()));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", jwtExpiration);
    }
}
