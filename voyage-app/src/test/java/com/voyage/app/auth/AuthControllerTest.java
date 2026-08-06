package com.voyage.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void register_returnsCreatedWithTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("alice", "alice@test.com", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_duplicateUsername_returnsConflict() throws Exception {
        RegisterRequest req = new RegisterRequest("bob", "bob@test.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second registration with the same username should fail
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_validCredentials_returnsTokens() throws Exception {
        RegisterRequest reg = new RegisterRequest("charlie", "charlie@test.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("charlie", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("dave", "dave@test.com", "password123"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("dave", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidToken_returnsNewAccessToken() throws Exception {
        // Register and login to get tokens
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("eve", "eve@test.com", "password123"))));

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("eve", "password123"))))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void logout_thenRefresh_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest("frank", "frank@test.com", "password123"))));

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("frank", "password123"))))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(loginBody).get("refreshToken").asText();

        // Logout revokes the refresh token
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isNoContent());

        // Subsequent refresh attempt should fail
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }
}
