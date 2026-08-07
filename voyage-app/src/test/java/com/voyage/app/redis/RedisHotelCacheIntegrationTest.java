package com.voyage.app.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.voyage.app.VoyageAppApplication;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelNotFoundException;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.hotel.HotelService;
import com.voyage.app.token.RefreshTokenRepository;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = VoyageAppApplication.class)
@ActiveProfiles("test")
class RedisHotelCacheIntegrationTest extends RedisIntegrationTestSupport {

  @Autowired HotelService hotelService;
  @Autowired HotelRepository hotelRepository;
  @Autowired StringRedisTemplate stringRedisTemplate;
  @Autowired CacheManager cacheManager;
  @Autowired UserRepository userRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired PasswordEncoder passwordEncoder;

  @BeforeEach
  void clearHotelsAndAuthenticateAdmin() {
    hotelRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    userRepository.deleteAll();
    User admin =
        userRepository.save(
            new User(
                "redis-cache-admin",
                "redis-cache-admin@test.com",
                passwordEncoder.encode("password123"),
                Role.ADMIN));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                admin.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()))));
  }

  @Test
  void findById_returnsCachedHotelWhenDatabaseRowDisappears() {
    Hotel savedHotel = hotelRepository.save(new Hotel("Cache Probe", "Tokyo", 210.0));

    Hotel firstRead = hotelService.findById(savedHotel.getId());
    hotelRepository.deleteById(savedHotel.getId());

    Hotel cachedRead = hotelService.findById(savedHotel.getId());

    assertEquals(firstRead.getId(), cachedRead.getId());
    assertEquals("Cache Probe", cachedRead.getName());
    assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey("hotelById::" + savedHotel.getId())));
    assertNotNull(cacheManager.getCache("hotelById"));
  }

  @Test
  void update_refreshesByIdCacheAndEvictsCityCache() {
    Hotel savedHotel = hotelService.save(new Hotel("City Cache", "Tokyo", 180.0));

    List<Hotel> initialTokyoResults = hotelService.findByCity("Tokyo");
    assertEquals(1, initialTokyoResults.size());
    assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey("hotelsByCity::Tokyo")));

    Hotel updatedHotel =
        hotelService.update(savedHotel.getId(), new Hotel("City Cache Updated", "Osaka", 195.0));

    assertEquals("City Cache Updated", hotelService.findById(savedHotel.getId()).getName());
    assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("hotelsByCity::Tokyo")));
    assertTrue(hotelService.findByCity("Tokyo").isEmpty());
    assertEquals(1, hotelService.findByCity("Osaka").size());
    assertEquals(updatedHotel.getId(), hotelService.findByCity("Osaka").getFirst().getId());
  }

  @Test
  void delete_evictsByIdCacheEntry() {
    Hotel savedHotel = hotelService.save(new Hotel("Delete Cache", "Delhi", 165.0));
    hotelService.findById(savedHotel.getId());

    assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey("hotelById::" + savedHotel.getId())));

    hotelService.delete(savedHotel.getId());

    assertFalse(
        Boolean.TRUE.equals(stringRedisTemplate.hasKey("hotelById::" + savedHotel.getId())));
    assertThrows(HotelNotFoundException.class, () -> hotelService.findById(savedHotel.getId()));
  }
}
