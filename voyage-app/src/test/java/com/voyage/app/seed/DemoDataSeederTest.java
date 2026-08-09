package com.voyage.app.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.user.Role;
import com.voyage.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "application.seed.enabled=true",
      "application.seed.on-startup-if-empty=false",
      "application.seed.hotels=3",
      "application.seed.inventory-days=2",
      "application.seed.customers=2"
    })
class DemoDataSeederTest {

  @Autowired DemoDataSeeder demoDataSeeder;
  @Autowired UserRepository userRepository;
  @Autowired HotelRepository hotelRepository;

  @Test
  void seed_isIdempotentForUsersAndCreatesHotels() {
    DemoDataSeeder.SeedResult first = demoDataSeeder.seed(false);
    assertThat(first.usersCreated()).isGreaterThanOrEqualTo(2);
    assertThat(first.hotelsCreated()).isEqualTo(3);
    assertThat(userRepository.existsByUsername(DemoDataSeeder.ADMIN_USERNAME)).isTrue();
    assertThat(userRepository.findByUsername(DemoDataSeeder.ADMIN_USERNAME))
        .get()
        .extracting(u -> u.getRole())
        .isEqualTo(Role.ADMIN);
    assertThat(hotelRepository.count()).isEqualTo(3);

    DemoDataSeeder.SeedResult second = demoDataSeeder.seed(false);
    assertThat(second.usersCreated()).isZero();
    assertThat(second.hotelsCreated()).isZero();
    assertThat(hotelRepository.count()).isEqualTo(3);

    DemoDataSeeder.SeedResult forced = demoDataSeeder.seed(true);
    assertThat(forced.hotelsCreated()).isEqualTo(3);
    assertThat(hotelRepository.count()).isEqualTo(6);
  }
}
