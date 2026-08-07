package com.voyage.app.user;

import com.voyage.app.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public UserProfileResponse getCurrentUser(Authentication authentication) {
    return userService.getByUsername(authentication.getName());
  }

  @PatchMapping("/me")
  public UserProfileResponse updateCurrentUser(
      Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(authentication.getName(), request);
  }

  @GetMapping
  public PageResponse<UserProfileResponse> getUsers(
      @RequestParam(required = false) Role role,
      @PageableDefault(size = 20, sort = "username") Pageable pageable) {
    return PageResponse.from(userService.findAll(role, pageable));
  }

  @PatchMapping("/{id}/role")
  public UserProfileResponse updateRole(
      @PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest request) {
    return userService.updateRole(id, request);
  }
}
