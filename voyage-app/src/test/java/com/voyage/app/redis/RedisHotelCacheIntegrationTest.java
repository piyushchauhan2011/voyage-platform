package com.voyage.app.redis;

import com.voyage.app.VoyageAppApplication;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelNotFoundException;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.hotel.HotelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = VoyageAppApplication.class)
@ActiveProfiles("test")
class RedisHotelCacheIntegrationTest extends RedisIntegrationTestSupport {

    @Autowired HotelService hotelService;
    @Autowired HotelRepository hotelRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void clearHotels() {
        hotelRepository.deleteAll();
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

        Hotel updatedHotel = hotelService.update(savedHotel.getId(), new Hotel("City Cache Updated", "Osaka", 195.0));

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

        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("hotelById::" + savedHotel.getId())));
        assertThrows(HotelNotFoundException.class, () -> hotelService.findById(savedHotel.getId()));
    }
}