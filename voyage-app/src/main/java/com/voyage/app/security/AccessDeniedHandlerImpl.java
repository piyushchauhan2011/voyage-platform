package com.voyage.app.security;

import com.voyage.app.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Called by ExceptionTranslationFilter when an authenticated user lacks the required role. Returns
 * 403 JSON — the user is known but not permitted.
 */
@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public AccessDeniedHandlerImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response
        .getWriter()
        .write(
            objectMapper.writeValueAsString(
                ApiError.of(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden",
                    "You do not have permission to access this resource",
                    request.getServletPath())));
  }
}
