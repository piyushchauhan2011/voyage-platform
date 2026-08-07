package com.voyage.app.user;

import com.voyage.app.exception.ConflictException;
import com.voyage.app.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getByUsername(String username) {
    return toResponse(findEntityByUsername(username));
  }

  @Transactional
  public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
    User user = findEntityByUsername(username);

    if (request.username() != null
        && !request.username().isBlank()
        && !request.username().equals(user.getUsername())) {
      if (userRepository.existsByUsernameAndIdNot(request.username(), user.getId())) {
        throw new ConflictException("Username already taken");
      }
      user.setUsername(request.username());
    }

    if (request.email() != null
        && !request.email().isBlank()
        && !request.email().equals(user.getEmail())) {
      if (userRepository.existsByEmailAndIdNot(request.email(), user.getId())) {
        throw new ConflictException("Email already registered");
      }
      user.setEmail(request.email());
    }

    return toResponse(userRepository.save(user));
  }

  @Transactional(readOnly = true)
  public Page<UserProfileResponse> findAll(Role role, Pageable pageable) {
    Page<User> page =
        role == null
            ? userRepository.findAll(pageable)
            : userRepository.findAllByRole(role, pageable);
    return page.map(this::toResponse);
  }

  @Transactional
  public UserProfileResponse updateRole(Long id, UpdateUserRoleRequest request) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    user.setRole(request.role());
    return toResponse(userRepository.save(user));
  }

  private User findEntityByUsername(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
  }

  private UserProfileResponse toResponse(User user) {
    return new UserProfileResponse(
        user.getId(), user.getUsername(), user.getEmail(), user.getRole());
  }
}
