package com.voyage.app.user;

import com.voyage.app.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String bearerTokenFor(Role role, String username) {
        User user = new User(username, username + "@test.com", passwordEncoder.encode("password123"), role);
        User savedUser = userRepository.save(user);
        return "Bearer " + jwtService.generateToken(savedUser);
    }

    @Test
    void getCurrentUser_returnsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearerTokenFor(Role.USER, "profile-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("profile-user"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void updateCurrentUser_updatesOwnProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearerTokenFor(Role.USER, "profile-update-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("updated-user", "updated@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("updated-user"))
                .andExpect(jsonPath("$.email").value("updated@test.com"));
    }

    @Test
    void getUsers_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearerTokenFor(Role.USER, "user-list-member")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsers_asAdmin_returnsPagedUsers() throws Exception {
        bearerTokenFor(Role.USER, "paged-user-1");
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "user-list-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void updateRole_asAdmin_changesUserRole() throws Exception {
        User target = userRepository.save(new User("role-target", "role-target@test.com", passwordEncoder.encode("password123"), Role.USER));

        mockMvc.perform(patch("/api/v1/users/{id}/role", target.getId())
                        .header("Authorization", bearerTokenFor(Role.ADMIN, "role-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}