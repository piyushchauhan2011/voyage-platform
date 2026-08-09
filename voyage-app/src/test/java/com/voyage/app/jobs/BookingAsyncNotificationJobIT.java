package com.voyage.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.voyage.app.booking.CreateBookingRequest;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationRepository;
import com.voyage.app.rabbitmq.RabbitMqIntegrationTestSupport;
import com.voyage.app.security.JwtService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.jobs.enabled=true",
      "application.jobs.async-notifications=true",
      "application.jobs.poll-interval-ms=60000"
    })
class BookingAsyncNotificationJobIT extends RabbitMqIntegrationTestSupport {

  @DynamicPropertySource
  static void enableJobs(DynamicPropertyRegistry registry) {
    registry.add("application.jobs.enabled", () -> true);
    registry.add("application.jobs.async-notifications", () -> true);
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JwtService jwtService;
  @Autowired HotelRepository hotelRepository;
  @Autowired InventoryService inventoryService;
  @Autowired NotificationRepository notificationRepository;
  @Autowired JobRunRepository jobRunRepository;

  private Hotel hotel;
  private String userToken;
  private User user;

  @BeforeEach
  void setUp() {
    hotel = hotelRepository.save(new Hotel("Async Jobs Hotel", "Paris", 190.0));
    user =
        userRepository.save(
            new User(
                "async-jobs-user",
                "async-jobs-user@test.com",
                passwordEncoder.encode("password123"),
                Role.CUSTOMER));
    userToken = "Bearer " + jwtService.generateToken(user);
    LocalDate checkIn = LocalDate.now().plusDays(3);
    inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn, 2);
    inventoryService.createInventory(hotel.getId(), RoomType.DOUBLE, checkIn.plusDays(1), 2);
  }

  @Test
  void createBooking_enqueuesEmailJobAndWorkerWritesNotification() throws Exception {
    LocalDate checkIn = LocalDate.now().plusDays(3);
    mockMvc
        .perform(
            post("/api/v1/bookings")
                .header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateBookingRequest(
                            hotel.getId(),
                            RoomType.DOUBLE,
                            checkIn,
                            checkIn.plusDays(2),
                            "approve"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    boolean observed = false;
    for (int attempt = 0; attempt < 50; attempt++) {
      boolean hasNotification =
          !notificationRepository
              .findByUserUsernameOrderByCreatedAtDesc(user.getUsername(), Pageable.ofSize(5))
              .getContent()
              .isEmpty();
      boolean hasJobRun =
          jobRunRepository.findTop50ByOrderByCreatedAtDesc().stream()
              .anyMatch(
                  run ->
                      "email.send".equals(run.getType())
                          && run.getSource() == JobSource.BOOKING
                          && run.getStatus() == JobRunStatus.SUCCESS);
      if (hasNotification && hasJobRun) {
        observed = true;
        break;
      }
      Thread.sleep(100);
    }
    assertThat(observed).as("expected async booking notification job run").isTrue();
  }
}
