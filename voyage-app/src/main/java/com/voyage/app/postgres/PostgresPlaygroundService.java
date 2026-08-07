package com.voyage.app.postgres;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class PostgresPlaygroundService {

    private static final String LAB_USER = "pg_lab_user";
    private static final int SEED_HOTEL_COUNT = 50;
    private static final int SEED_BOOKING_TARGET = 20_000;
    private static final int MIN_BOOKINGS_BEFORE_SKIP = 10_000;
    private static final int MAX_LOCK_HOLD_SECONDS = 10;

    private static final Map<String, IndexDefinition> INDEXES = Map.of(
            "idx_booking_hotel", new IndexDefinition("idx_booking_hotel", "bookings", "hotel_id"),
            "idx_booking_hotel_checkin", new IndexDefinition("idx_booking_hotel_checkin", "bookings", "hotel_id, check_in_date"),
            "idx_booking_user_status", new IndexDefinition("idx_booking_user_status", "bookings", "user_id, status"),
            "idx_hotel_city", new IndexDefinition("idx_hotel_city", "hotels", "city")
    );

    private static final Set<String> EXPLAIN_SCENARIOS = Set.of(
            "booking_by_hotel",
            "booking_by_hotel_checkin",
            "hotel_by_city"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;

    public PostgresPlaygroundService(JdbcTemplate jdbcTemplate,
                                     DataSource dataSource,
                                     PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
    }

    public SeedResult seed() {
        ensureLabUser();
        int hotelsCreated = ensureLabHotels();
        Long demoHotelId = findDemoHotelId();
        int bookingsBefore = count("SELECT COUNT(*) FROM bookings");
        int bookingsInserted = 0;
        if (bookingsBefore < MIN_BOOKINGS_BEFORE_SKIP) {
            bookingsInserted = insertVolumeBookings(demoHotelId);
        }
        analyze("hotels");
        analyze("bookings");
        return new SeedResult(
                hotelsCreated,
                bookingsInserted,
                count("SELECT COUNT(*) FROM hotels"),
                count("SELECT COUNT(*) FROM bookings"),
                demoHotelId
        );
    }

    public ExplainResult explain(String scenario) {
        if (scenario == null || !EXPLAIN_SCENARIOS.contains(scenario)) {
            throw new IllegalArgumentException(
                    "scenario must be one of: booking_by_hotel, booking_by_hotel_checkin, hotel_by_city");
        }
        Long hotelId = findDemoHotelId();
        if (hotelId == null && !"hotel_by_city".equals(scenario)) {
            throw new IllegalStateException("No lab hotel found. Call POST /seed first.");
        }

        String sql = switch (scenario) {
            case "booking_by_hotel" ->
                    "SELECT * FROM bookings WHERE hotel_id = " + hotelId;
            case "booking_by_hotel_checkin" ->
                    "SELECT id, hotel_id, check_in_date, status FROM bookings "
                            + "WHERE hotel_id = " + hotelId
                            + " AND check_in_date BETWEEN DATE '2026-03-01' AND DATE '2026-03-31'";
            case "hotel_by_city" ->
                    "SELECT * FROM hotels WHERE city = 'Tokyo'";
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };

        Instant started = Instant.now();
        String plan = runExplain(sql);
        long elapsedMs = Duration.between(started, Instant.now()).toMillis();
        return new ExplainResult(scenario, sql, plan, elapsedMs, detectScanType(plan));
    }

    public IndexActionResult dropIndex(String name) {
        IndexDefinition definition = requireIndex(name);
        try {
            jdbcTemplate.execute("DROP INDEX IF EXISTS " + definition.name());
        } catch (DataAccessException ex) {
            if (!isPostgres()) {
                throw new IllegalStateException(
                        "Cannot drop " + definition.name()
                                + " on H2 (index may be owned by a foreign key). "
                                + "Run index drop/create demos against PostgreSQL.",
                        ex);
            }
            throw ex;
        }
        return new IndexActionResult(definition.name(), "dropped", indexExists(definition.name()));
    }

    public IndexActionResult createIndex(String name) {
        IndexDefinition definition = requireIndex(name);
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS " + definition.name()
                        + " ON " + definition.table() + " (" + definition.columns() + ")");
        analyze(definition.table());
        return new IndexActionResult(definition.name(), "created", indexExists(definition.name()));
    }

    public Map<String, Boolean> listIndexes() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        INDEXES.keySet().stream().sorted().forEach(name -> status.put(name, indexExists(name)));
        return status;
    }

    public PartitionSetupResult setupPartitioning() {
        requirePostgres("partitioning");
        jdbcTemplate.execute("DROP TABLE IF EXISTS pg_lab_bookings CASCADE");
        jdbcTemplate.execute("""
                CREATE TABLE pg_lab_bookings (
                    id            BIGSERIAL,
                    hotel_id      BIGINT NOT NULL,
                    check_in_date DATE NOT NULL,
                    status        TEXT NOT NULL,
                    total_price   NUMERIC(12, 2) NOT NULL,
                    PRIMARY KEY (id, check_in_date)
                ) PARTITION BY RANGE (check_in_date)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pg_lab_bookings_2026_q1 PARTITION OF pg_lab_bookings
                    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01')
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pg_lab_bookings_2026_q2 PARTITION OF pg_lab_bookings
                    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01')
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pg_lab_bookings_2026_q3 PARTITION OF pg_lab_bookings
                    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01')
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pg_lab_bookings_2026_q4 PARTITION OF pg_lab_bookings
                    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01')
                """);
        int rows = jdbcTemplate.update("""
                INSERT INTO pg_lab_bookings (hotel_id, check_in_date, status, total_price)
                SELECT
                    1 + (g % 20),
                    DATE '2026-01-01' + ((g % 365) || ' days')::interval,
                    (ARRAY['PENDING', 'CONFIRMED', 'CANCELLED'])[1 + (g % 3)],
                    (80 + (g % 100))::numeric
                FROM generate_series(1, 20000) AS g
                """);
        analyze("pg_lab_bookings");
        return new PartitionSetupResult(true, rows, List.of(
                "pg_lab_bookings_2026_q1",
                "pg_lab_bookings_2026_q2",
                "pg_lab_bookings_2026_q3",
                "pg_lab_bookings_2026_q4"
        ));
    }

    public ExplainResult explainPartitioning() {
        requirePostgres("partitioning");
        String sql = """
                SELECT COUNT(*)
                FROM pg_lab_bookings
                WHERE check_in_date >= DATE '2026-01-15'
                  AND check_in_date < DATE '2026-02-15'
                """;
        Instant started = Instant.now();
        String plan = runExplain(sql);
        long elapsedMs = Duration.between(started, Instant.now()).toMillis();
        return new ExplainResult("partition_pruning", sql, plan, elapsedMs, detectScanType(plan));
    }

    public LockHoldResult holdLock(Integer seconds) {
        int holdSeconds = seconds == null ? 3 : seconds;
        if (holdSeconds < 1 || holdSeconds > MAX_LOCK_HOLD_SECONDS) {
            throw new IllegalArgumentException("seconds must be between 1 and " + MAX_LOCK_HOLD_SECONDS);
        }

        Long inventoryId = jdbcTemplate.query("""
                SELECT id FROM room_inventory ORDER BY id LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null);

        if (inventoryId == null) {
            // Fall back to locking a booking row if inventory is empty
            Long bookingId = jdbcTemplate.query("""
                    SELECT id FROM bookings ORDER BY id LIMIT 1
                    """, rs -> rs.next() ? rs.getLong(1) : null);
            if (bookingId == null) {
                throw new IllegalStateException("No room_inventory or bookings rows to lock. Seed data first.");
            }
            return holdRowLock("bookings", bookingId, holdSeconds);
        }
        return holdRowLock("room_inventory", inventoryId, holdSeconds);
    }

    public IsolationDemoResult isolationDemo(String scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario is required: read_committed_non_repeatable or repeatable_read_stable");
        }
        return switch (scenario) {
            case "read_committed_non_repeatable" -> runIsolationDemo(false);
            case "repeatable_read_stable" -> runIsolationDemo(true);
            default -> throw new IllegalArgumentException(
                    "scenario must be read_committed_non_repeatable or repeatable_read_stable");
        };
    }

    private IsolationDemoResult runIsolationDemo(boolean repeatableRead) {
        Long hotelId = findDemoHotelId();
        if (hotelId == null) {
            throw new IllegalStateException("No lab hotel found. Call POST /seed first.");
        }

        String isolation = repeatableRead ? "REPEATABLE READ" : "READ COMMITTED";
        Double firstRead;
        Double secondRead;
        Double afterReset;

        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection()) {
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);
            try (Statement stmtA = sessionA.createStatement()) {
                stmtA.execute("SET TRANSACTION ISOLATION LEVEL " + isolation);
            }

            firstRead = readPrice(sessionA, hotelId);

            try (PreparedStatement update = sessionB.prepareStatement(
                    "UPDATE hotels SET price_per_night = price_per_night + 1 WHERE id = ?")) {
                update.setLong(1, hotelId);
                update.executeUpdate();
                sessionB.commit();
            }

            secondRead = readPrice(sessionA, hotelId);
            sessionA.rollback();

            // Restore original price (+1 was applied)
            try (PreparedStatement reset = sessionB.prepareStatement(
                    "UPDATE hotels SET price_per_night = price_per_night - 1 WHERE id = ?")) {
                sessionB.setAutoCommit(true);
                reset.setLong(1, hotelId);
                reset.executeUpdate();
            }
            afterReset = jdbcTemplate.queryForObject(
                    "SELECT price_per_night FROM hotels WHERE id = ?", Double.class, hotelId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Isolation demo failed: " + ex.getMessage(), ex);
        }

        String observation = repeatableRead
                ? "Second read matched the first (snapshot stayed stable under REPEATABLE READ)."
                : "Second read saw session B's committed update (non-repeatable read under READ COMMITTED).";

        return new IsolationDemoResult(
                repeatableRead ? "repeatable_read_stable" : "read_committed_non_repeatable",
                isolation,
                hotelId,
                firstRead,
                secondRead,
                afterReset,
                observation
        );
    }

    private LockHoldResult holdRowLock(String table, long id, int holdSeconds) {
        Instant started = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM " + table + " WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Row disappeared: " + table + "#" + id);
                    }
                }
                TimeUnit.SECONDS.sleep(holdSeconds);
            }
            connection.rollback();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock hold interrupted", ex);
        } catch (SQLException ex) {
            throw new IllegalStateException("Lock hold failed: " + ex.getMessage(), ex);
        }
        long elapsedMs = Duration.between(started, Instant.now()).toMillis();
        return new LockHoldResult(table, id, holdSeconds, elapsedMs,
                "Held FOR UPDATE for " + holdSeconds + "s then rolled back. "
                        + "Open another session and try SELECT … FOR UPDATE on the same row to observe a lock wait.");
    }

    private void ensureLabUser() {
        Integer existing = jdbcTemplate.query("""
                SELECT id FROM users WHERE username = ?
                """, rs -> rs.next() ? rs.getInt(1) : null, LAB_USER);
        if (existing != null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password, role)
                VALUES (?, ?, ?, ?)
                """, LAB_USER, LAB_USER + "@voyage.local", passwordEncoder.encode("password123"), "CUSTOMER");
    }

    private int ensureLabHotels() {
        int created = 0;
        for (int g = 1; g <= SEED_HOTEL_COUNT; g++) {
            String name = "Lab Hotel " + g;
            Integer existing = jdbcTemplate.query("""
                    SELECT id FROM hotels WHERE name = ?
                    """, rs -> rs.next() ? 1 : null, name);
            if (existing != null) {
                continue;
            }
            String city = switch (g % 5) {
                case 0 -> "Tokyo";
                case 1 -> "Paris";
                case 2 -> "Lisbon";
                case 3 -> "Dubai";
                default -> "Copenhagen";
            };
            jdbcTemplate.update("""
                    INSERT INTO hotels (name, city, price_per_night)
                    VALUES (?, ?, ?)
                    """, name, city, 80.0 + (g % 40) * 10);
            created++;
        }
        return created;
    }

    private int insertVolumeBookings(Long preferredHotelId) {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, LAB_USER);
        List<Long> hotelIds = jdbcTemplate.query(
                "SELECT id FROM hotels WHERE name LIKE 'Lab Hotel %' ORDER BY id",
                (rs, rowNum) -> rs.getLong(1));
        if (hotelIds.isEmpty()) {
            throw new IllegalStateException("No lab hotels available for booking seed");
        }

        if (isPostgres()) {
            return jdbcTemplate.update("""
                    INSERT INTO bookings (
                        user_id, hotel_id, room_type, check_in_date, check_out_date,
                        status, total_price, created_at
                    )
                    SELECT
                        ?,
                        h.id,
                        CASE (g % 3)
                            WHEN 0 THEN 'SINGLE'
                            WHEN 1 THEN 'DOUBLE'
                            ELSE 'SUITE'
                        END,
                        DATE '2026-01-01' + (g % 180),
                        DATE '2026-01-01' + (g % 180) + 2,
                        CASE (g % 3)
                            WHEN 0 THEN 'PENDING'
                            WHEN 1 THEN 'CONFIRMED'
                            ELSE 'CANCELLED'
                        END,
                        (100 + (g % 50))::numeric,
                        NOW()
                    FROM generate_series(1, ?) AS g
                    CROSS JOIN LATERAL (
                        SELECT id FROM hotels WHERE name LIKE 'Lab Hotel %'
                        ORDER BY id
                        OFFSET (g % ?) LIMIT 1
                    ) h
                    """, userId, SEED_BOOKING_TARGET, hotelIds.size());
        }

        // H2 / non-Postgres fallback: smaller loop for tests
        int inserted = 0;
        int target = Math.min(200, SEED_BOOKING_TARGET);
        for (int g = 1; g <= target; g++) {
            Long hotelId = hotelIds.get((g - 1) % hotelIds.size());
            if (preferredHotelId != null && g % 10 == 0) {
                hotelId = preferredHotelId;
            }
            String roomType = switch (g % 3) {
                case 0 -> "SINGLE";
                case 1 -> "DOUBLE";
                default -> "SUITE";
            };
            String status = switch (g % 3) {
                case 0 -> "PENDING";
                case 1 -> "CONFIRMED";
                default -> "CANCELLED";
            };
            jdbcTemplate.update("""
                    INSERT INTO bookings (
                        user_id, hotel_id, room_type, check_in_date, check_out_date,
                        status, total_price, created_at
                    ) VALUES (?, ?, ?, DATEADD('DAY', ?, DATE '2026-01-01'), DATEADD('DAY', ?, DATE '2026-01-01'), ?, ?, CURRENT_TIMESTAMP)
                    """, userId, hotelId, roomType, g % 180, (g % 180) + 2, status, 100 + (g % 50));
            inserted++;
        }
        return inserted;
    }

    private Long findDemoHotelId() {
        return jdbcTemplate.query("""
                SELECT id FROM hotels WHERE name = 'Lab Hotel 10' LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null);
    }

    private String runExplain(String sql) {
        String explainSql = isPostgres()
                ? "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql
                : "EXPLAIN " + sql;
        List<String> lines = jdbcTemplate.query(explainSql, (rs, rowNum) -> rs.getString(1));
        return String.join("\n", lines);
    }

    private boolean indexExists(String name) {
        if (isPostgres()) {
            Boolean exists = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE c.relkind = 'i'
                          AND c.relname = ?
                          AND n.nspname = current_schema()
                    )
                    """, Boolean.class, name);
            return Boolean.TRUE.equals(exists);
        }
        // H2: INFORMATION_SCHEMA
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = ?
                """, Integer.class, name.toUpperCase(Locale.ROOT));
        return count != null && count > 0;
    }

    private IndexDefinition requireIndex(String name) {
        IndexDefinition definition = INDEXES.get(name);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unknown index. Allowed: " + String.join(", ", INDEXES.keySet().stream().sorted().toList()));
        }
        return definition;
    }

    private void analyze(String table) {
        if (isPostgres()) {
            jdbcTemplate.execute("ANALYZE " + table);
        }
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Double readPrice(Connection connection, long hotelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT price_per_night FROM hotels WHERE id = ?")) {
            statement.setLong(1, hotelId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Hotel not found: " + hotelId);
                }
                return rs.getDouble(1);
            }
        }
    }

    private void requirePostgres(String feature) {
        if (!isPostgres()) {
            throw new IllegalStateException(feature + " requires PostgreSQL (not available on H2 test profile)");
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }

    private static String detectScanType(String plan) {
        String lower = plan.toLowerCase(Locale.ROOT);
        if (lower.contains("index only scan") || lower.contains("index scan") || lower.contains("bitmap index")) {
            return "INDEX";
        }
        if (lower.contains("seq scan") || lower.contains("table scan")) {
            return "SEQ";
        }
        return "UNKNOWN";
    }

    private record IndexDefinition(String name, String table, String columns) {
    }
}

record SeedResult(int hotelsCreated, int bookingsInserted, int hotelCount, int bookingCount, Long demoHotelId) {
}

record ExplainResult(String scenario, String sql, String plan, long elapsedMs, String scanType) {
}

record IndexActionResult(String name, String action, boolean exists) {
}

record PartitionSetupResult(boolean ready, int rowsInserted, List<String> partitions) {
}

record LockHoldResult(String table, long rowId, int heldSeconds, long elapsedMs, String tip) {
}

record IsolationDemoResult(
        String scenario,
        String isolationLevel,
        long hotelId,
        Double firstRead,
        Double secondRead,
        Double priceAfterReset,
        String observation
) {
}
