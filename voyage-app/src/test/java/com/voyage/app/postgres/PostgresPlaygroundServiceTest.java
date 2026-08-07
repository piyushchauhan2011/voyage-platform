package com.voyage.app.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PostgresPlaygroundServiceTest {

    @Autowired
    PostgresPlaygroundService postgresPlaygroundService;

    @Autowired
    DataSource dataSource;

    @Test
    void seedAndExplain_producePlanText() {
        SeedResult seed = postgresPlaygroundService.seed();
        assertNotNull(seed.demoHotelId());
        assertTrue(seed.hotelCount() >= 50);
        assertTrue(seed.bookingCount() > 0);

        ExplainResult withIndex = postgresPlaygroundService.explain("booking_by_hotel");
        assertFalse(withIndex.plan().isBlank());
        assertTrue(withIndex.sql().contains("hotel_id"));

        if (isPostgres()) {
            postgresPlaygroundService.dropIndex("idx_booking_hotel");
            ExplainResult withoutIndex = postgresPlaygroundService.explain("booking_by_hotel");
            assertFalse(withoutIndex.plan().isBlank());
            postgresPlaygroundService.createIndex("idx_booking_hotel");
            assertTrue(postgresPlaygroundService.listIndexes().get("idx_booking_hotel"));
        }
    }

    @Test
    void isolationDemo_readCommitted_seesUpdate() {
        postgresPlaygroundService.seed();
        IsolationDemoResult result = postgresPlaygroundService.isolationDemo("read_committed_non_repeatable");
        assertNotNull(result.firstRead());
        assertNotNull(result.secondRead());
        assertTrue(result.secondRead() > result.firstRead());
    }

    @Test
    void isolationDemo_repeatableRead_keepsSnapshot() {
        postgresPlaygroundService.seed();
        IsolationDemoResult result = postgresPlaygroundService.isolationDemo("repeatable_read_stable");
        assertNotNull(result.firstRead());
        assertNotNull(result.secondRead());
        assertTrue(Math.abs(result.firstRead() - result.secondRead()) < 0.0001);
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
