package com.voyage.app.seed;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.hotel.SaasPlan;
import com.voyage.app.inventory.RoomInventory;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataFaker demo catalogue for local learning. Auto-runs when the DB is empty; ADMIN can also call
 * {@code POST /api/seed/demo} to top up hotels/inventory.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "application.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

  public static final String ADMIN_USERNAME = "admin";
  public static final String MANAGER_USERNAME = "manager";
  public static final String DEFAULT_PASSWORD = "password123";

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
  private static final String[] CITIES = {
    "Bangkok", "Tokyo", "Paris", "Rome", "London", "New York", "Singapore", "Sydney", "Barcelona"
  };
  private static final SaasPlan[] PLANS = SaasPlan.values();

  private final UserRepository userRepository;
  private final HotelRepository hotelRepository;
  private final RoomInventoryRepository roomInventoryRepository;
  private final PasswordEncoder passwordEncoder;
  private final boolean onStartupIfEmpty;
  private final int hotelCount;
  private final int inventoryDays;
  private final int customerCount;
  private final Faker faker = new Faker(Locale.ENGLISH);

  public DemoDataSeeder(
      UserRepository userRepository,
      HotelRepository hotelRepository,
      RoomInventoryRepository roomInventoryRepository,
      PasswordEncoder passwordEncoder,
      @Value("${application.seed.on-startup-if-empty:true}") boolean onStartupIfEmpty,
      @Value("${application.seed.hotels:20}") int hotelCount,
      @Value("${application.seed.inventory-days:30}") int inventoryDays,
      @Value("${application.seed.customers:5}") int customerCount) {
    this.userRepository = userRepository;
    this.hotelRepository = hotelRepository;
    this.roomInventoryRepository = roomInventoryRepository;
    this.passwordEncoder = passwordEncoder;
    this.onStartupIfEmpty = onStartupIfEmpty;
    this.hotelCount = Math.max(hotelCount, 0);
    this.inventoryDays = Math.max(inventoryDays, 0);
    this.customerCount = Math.max(customerCount, 0);
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!onStartupIfEmpty) {
      return;
    }
    if (hotelRepository.count() > 0 && userRepository.existsByUsername(ADMIN_USERNAME)) {
      log.info("Demo seed skipped — database already has hotels and admin user");
      return;
    }
    SeedResult result = seed(false);
    log.info(
        "Demo seed complete: usersCreated={}, hotelsCreated={}, inventoryRows={}",
        result.usersCreated(),
        result.hotelsCreated(),
        result.inventoryRows());
  }

  /**
   * @param forceHotels when true (ADMIN API), always create a new hotel batch even if hotels exist
   */
  @Transactional
  public SeedResult seed(boolean forceHotels) {
    int usersCreated = ensureUsers();
    int hotelsCreated = 0;
    int inventoryRows = 0;

    if (forceHotels || hotelRepository.count() == 0) {
      User manager = userRepository.findByUsername(MANAGER_USERNAME).orElse(null);
      List<Hotel> hotels = buildHotels(manager, forceHotels);
      List<Hotel> saved = hotelRepository.saveAll(hotels);
      hotelsCreated = saved.size();
      inventoryRows = seedInventory(saved);
    }

    return new SeedResult(
        usersCreated,
        hotelsCreated,
        inventoryRows,
        userRepository.count(),
        hotelRepository.count(),
        ADMIN_USERNAME,
        DEFAULT_PASSWORD,
        "DataFaker filled users/hotels/inventory. Login as admin / password123 for labs.",
        "Flyway owns schema (validate). Re-seed via POST /api/seed/demo when you need more hotels.");
  }

  private int ensureUsers() {
    int created = 0;
    String encoded = passwordEncoder.encode(DEFAULT_PASSWORD);

    if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
      userRepository.save(new User(ADMIN_USERNAME, "admin@voyage.local", encoded, Role.ADMIN));
      created++;
    }
    if (!userRepository.existsByUsername(MANAGER_USERNAME)) {
      userRepository.save(
          new User(MANAGER_USERNAME, "manager@voyage.local", encoded, Role.HOTEL_MANAGER));
      created++;
    }

    for (int i = 1; i <= customerCount; i++) {
      String username = "customer" + i;
      if (userRepository.existsByUsername(username)) {
        continue;
      }
      String email = username + "@voyage.local";
      if (userRepository.existsByEmail(email)) {
        email = faker.internet().emailAddress();
      }
      userRepository.save(new User(username, email, encoded, Role.CUSTOMER));
      created++;
    }
    return created;
  }

  private List<Hotel> buildHotels(User manager, boolean forceUnique) {
    List<Hotel> hotels = new ArrayList<>(hotelCount);
    String suffix = forceUnique ? "-" + UUID.randomUUID().toString().substring(0, 8) : "";
    for (int i = 0; i < hotelCount; i++) {
      String city = CITIES[i % CITIES.length];
      String name =
          faker.company().name().replace(",", "")
              + " "
              + faker.commerce().productName().split(" ")[0]
              + " Hotel"
              + suffix;
      double price = 80 + faker.number().numberBetween(20, 420);
      Hotel hotel =
          new Hotel(
              name,
              city,
              price,
              faker.lorem().paragraph(2),
              String.join(
                  ",",
                  faker.commerce().productName(),
                  faker.commerce().material(),
                  "WiFi",
                  "Pool"));
      hotel.setSaasPlan(PLANS[i % PLANS.length]);
      hotel.setStarRating(faker.number().numberBetween(3, 6));
      hotel.setGuestRating(3.5 + faker.number().randomDouble(1, 0, 15) / 10.0);
      hotel.setReviewCount(faker.number().numberBetween(12, 2400));
      hotel.setAddress(faker.address().streetAddress());
      hotel.setNeighborhood(faker.address().cityName());
      hotel.setPhone(faker.phoneNumber().phoneNumber());
      hotel.setCheckInFrom("14:00");
      hotel.setCheckOutUntil("11:00");
      hotel.setImageUrl("https://picsum.photos/seed/voyage-" + (i + 1) + "/800/500");
      if (manager != null && i % 3 == 0) {
        hotel.setManager(manager);
      }
      hotels.add(hotel);
    }
    return hotels;
  }

  private int seedInventory(List<Hotel> hotels) {
    if (hotels.isEmpty() || inventoryDays == 0) {
      return 0;
    }
    LocalDate today = LocalDate.now();
    List<RoomInventory> rows = new ArrayList<>();
    for (Hotel hotel : hotels) {
      for (int day = 0; day < inventoryDays; day++) {
        LocalDate date = today.plusDays(day);
        rows.add(new RoomInventory(hotel, RoomType.SINGLE, date, 4));
        rows.add(new RoomInventory(hotel, RoomType.DOUBLE, date, 6));
        rows.add(new RoomInventory(hotel, RoomType.SUITE, date, 2));
      }
    }
    roomInventoryRepository.saveAll(rows);
    return rows.size();
  }

  public record SeedResult(
      int usersCreated,
      int hotelsCreated,
      int inventoryRows,
      long totalUsers,
      long totalHotels,
      String adminUsername,
      String defaultPassword,
      String observation,
      String tip) {}
}
