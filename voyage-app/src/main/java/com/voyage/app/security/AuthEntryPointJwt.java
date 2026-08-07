package com.voyage.app.security;

import com.voyage.app.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Called by ExceptionTranslationFilter when an unauthenticated request hits a protected endpoint.
 * Returns a 401 JSON body instead of Spring Security's default HTML error page.
 *
 * 401 Unauthorized  — no valid credentials were provided (missing or invalid token)
 * 403 Forbidden     — credentials are valid but the role doesn't permit access → AccessDeniedHandlerImpl
 */
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public AuthEntryPointJwt(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", authException.getMessage(), request.getServletPath())
        ));
    }
}
