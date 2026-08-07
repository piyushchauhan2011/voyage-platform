package com.voyage.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

/**
 * SecurityFilterChain replaces the deprecated WebSecurityConfigurerAdapter (removed in Spring Security 6).
 *
 * Filter execution order for an authenticated request:
 *   SecurityContextHolderFilter
 *   → JwtAuthenticationFilter       (reads Bearer token, sets SecurityContext)
 *   → AnonymousAuthenticationFilter (sets ROLE_ANONYMOUS if still no auth)
 *   → ExceptionTranslationFilter    (routes AuthenticationException→401, AccessDeniedException→403)
 *   → AuthorizationFilter           (enforces requestMatchers rules)
 *   → DispatcherServlet → Controller
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final AuthEntryPointJwt authEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          UserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder,
                          AuthEntryPointJwt authEntryPoint,
                          AccessDeniedHandlerImpl accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // REST APIs are stateless — CSRF tokens are only needed for browser-based session auth
            .csrf(AbstractHttpConfigurer::disable)
            // Never create a server-side session; every request must carry its own JWT
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Allow Spring Boot's error dispatcher so a real error page/message reaches the client
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/ui/**", "/css/**", "/js/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/kafka/**").hasRole("ADMIN")
                .requestMatchers("/api/redis/**").hasRole("ADMIN")
                .requestMatchers("/api/postgres/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/inventory/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventory/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/bookings/**").authenticated()
                .requestMatchers("/api/v1/notifications/**").authenticated()
                // Hotel reads are public — no account required to browse
                .requestMatchers(HttpMethod.GET, "/api/v1/hotels/**").permitAll()
                // Hotel writes are restricted to admins
                .requestMatchers(HttpMethod.POST, "/api/v1/hotels/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/hotels/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/hotels/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)    // 401 — unauthenticated
                .accessDeniedHandler(accessDeniedHandler)    // 403 — authenticated but wrong role
            )
            .authenticationProvider(daoAuthenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Exposes the AuthenticationManager as a bean so AuthService can call
     * authenticationManager.authenticate() directly during login.
     * AuthenticationConfiguration auto-discovers the DaoAuthenticationProvider bean above.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
