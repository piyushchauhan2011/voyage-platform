package com.voyage.app.jpa;

import com.voyage.app.booking.Booking;
import com.voyage.app.booking.BookingCriteriaRepository;
import com.voyage.app.booking.BookingRepository;
import com.voyage.app.booking.BookingSearchCriteria;
import com.voyage.app.booking.BookingService;
import com.voyage.app.booking.BookingSpec;
import com.voyage.app.booking.BookingStatus;
import com.voyage.app.booking.CreateBookingRequest;
import com.voyage.app.exception.PaymentFailedException;
import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.InventoryService;
import com.voyage.app.inventory.RoomInventory;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import com.voyage.app.notification.NotificationService;
import com.voyage.app.notification.NotificationWriter;
import com.voyage.app.payment.PaymentRepository;
import com.voyage.app.payment.PaymentService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JpaPlaygroundService {

  static final String LAB_USER = "jpa_lab_user";
  static final String LAB_HOTEL_NAME = "JPA Lab Hotel";
  private static final RoomType LAB_ROOM_TYPE = RoomType.DOUBLE;
  private static final int SAMPLE_BOOKINGS = 5;
  private static final int LOCK_HOLD_MS = 1500;

  private final HotelRepository hotelRepository;
  private final UserRepository userRepository;
  private final BookingRepository bookingRepository;
  private final BookingCriteriaRepository bookingCriteriaRepository;
  private final BookingService bookingService;
  private final InventoryService inventoryService;
  private final RoomInventoryRepository roomInventoryRepository;
  private final PaymentRepository paymentRepository;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate writeTx;
  private final TransactionTemplate readTx;
  private final PlatformTransactionManager transactionManager;

  @PersistenceContext private EntityManager entityManager;

  public JpaPlaygroundService(
      HotelRepository hotelRepository,
      UserRepository userRepository,
      BookingRepository bookingRepository,
      BookingCriteriaRepository bookingCriteriaRepository,
      BookingService bookingService,
      InventoryService inventoryService,
      RoomInventoryRepository roomInventoryRepository,
      PaymentRepository paymentRepository,
      PasswordEncoder passwordEncoder,
      PlatformTransactionManager transactionManager) {
    this.hotelRepository = hotelRepository;
    this.userRepository = userRepository;
    this.bookingRepository = bookingRepository;
    this.bookingCriteriaRepository = bookingCriteriaRepository;
    this.bookingService = bookingService;
    this.inventoryService = inventoryService;
    this.roomInventoryRepository = roomInventoryRepository;
    this.paymentRepository = paymentRepository;
    this.passwordEncoder = passwordEncoder;
    this.transactionManager = transactionManager;
    this.writeTx = new TransactionTemplate(transactionManager);
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
  }

  public LabSeedResult seed() {
    LabFixtures fixtures = writeTx.execute(status -> ensureFixtures(true));
    ensureSampleBookings(fixtures);
    return new LabSeedResult(
        fixtures.hotel().getId(),
        fixtures.user().getUsername(),
        fixtures.checkIn().toString(),
        fixtures.checkOut().toString(),
        bookingRepository.count(),
        availableRooms(fixtures.hotel().getId(), fixtures.checkIn()),
        "Lab hotel, user, inventory, and sample bookings ready.");
  }

  public LifecyclePersistResult persistHotel() {
    return writeTx.execute(
        status -> {
          Hotel hotel =
              new Hotel("Lifecycle Hotel " + Instant.now().toEpochMilli(), "Lifecycle City", 99.0);
          String beforePersist = "NEW (id=" + hotel.getId() + ")";
          entityManager.persist(hotel);
          entityManager.flush();
          return new LifecyclePersistResult(
              hotel.getId(),
              hotel.getName(),
              beforePersist,
              "MANAGED (id assigned, dirty checking active)",
              "Hotel.java documents NEW → MANAGED → DETACHED → REMOVED");
        });
  }

  public LifecycleDetachResult detachAndMutate() {
    return writeTx.execute(
        status -> {
          Hotel hotel =
              hotelRepository.findAll().stream()
                  .filter(
                      h ->
                          h.getName().startsWith("Lifecycle Hotel")
                              || LAB_HOTEL_NAME.equals(h.getName()))
                  .findFirst()
                  .orElseGet(
                      () -> hotelRepository.save(new Hotel(LAB_HOTEL_NAME, "Lab City", 150.0)));

          Long id = hotel.getId();
          String originalName = hotel.getName();
          String mutatedName = originalName + " [detached-edit]";

          entityManager.clear();
          Hotel detached = entityManager.find(Hotel.class, id);
          entityManager.detach(detached);
          detached.setName(mutatedName);
          entityManager.flush();

          Hotel reloaded = entityManager.find(Hotel.class, id);
          boolean persisted = mutatedName.equals(reloaded.getName());
          return new LifecycleDetachResult(
              id,
              originalName,
              mutatedName,
              reloaded.getName(),
              persisted,
              persisted
                  ? "Unexpected: detached mutate was written."
                  : "DETACHED mutations are ignored until merge(); flush did not update the row.");
        });
  }

  public LoadingDemoResult nPlusOne() {
    LabFixtures fixtures = prepareWithSamples();
    return readTx.execute(
        status -> {
          Statistics stats = beginStats();
          List<Booking> bookings =
              entityManager
                  .createQuery(
                      """
                                    select booking
                                    from Booking booking
                                    where booking.hotel.id = :hotelId
                                    order by booking.checkIn asc
                                    """,
                      Booking.class)
                  .setParameter("hotelId", fixtures.hotel().getId())
                  .setMaxResults(SAMPLE_BOOKINGS)
                  .getResultList();

          List<String> hotelNames = new ArrayList<>();
          for (Booking booking : bookings) {
            hotelNames.add(booking.getHotel().getName());
          }

          long queryCount = stats.getPrepareStatementCount();
          endStats(stats);
          return new LoadingDemoResult(
              "nplus1",
              bookings.size(),
              queryCount,
              hotelNames.stream().distinct().toList(),
              "1 select for bookings + N selects for LAZY hotels when touching getHotel().getName()",
              "Compare with POST /loading/entity-graph");
        });
  }

  public LoadingDemoResult entityGraph() {
    LabFixtures fixtures = prepareWithSamples();
    return readTx.execute(
        status -> {
          Statistics stats = beginStats();
          List<Booking> bookings =
              bookingRepository.findByHotelAndStatus(
                  fixtures.hotel().getId(), BookingStatus.CONFIRMED);
          if (bookings.size() > SAMPLE_BOOKINGS) {
            bookings = bookings.subList(0, SAMPLE_BOOKINGS);
          }

          List<String> hotelNames = new ArrayList<>();
          for (Booking booking : bookings) {
            hotelNames.add(booking.getHotel().getName());
          }

          long queryCount = stats.getPrepareStatementCount();
          endStats(stats);
          return new LoadingDemoResult(
              "entity-graph",
              bookings.size(),
              queryCount,
              hotelNames.stream().distinct().toList(),
              "@EntityGraph on findByHotelAndStatus loads hotel + user with the booking query",
              "Expect queryCount close to 1 for the sample size");
        });
  }

  public QueryDemoResult queryJpql() {
    LabFixtures fixtures = prepareWithSamples();
    return readTx.execute(
        status -> {
          Statistics stats = beginStats();
          List<Booking> bookings = bookingRepository.findByUsername(fixtures.user().getUsername());
          long count =
              bookingRepository.countByHotelAndDateRange(
                  fixtures.hotel().getId(),
                  fixtures.checkIn().minusDays(30),
                  fixtures.checkOut().plusDays(30));
          long queryCount = stats.getPrepareStatementCount();
          endStats(stats);

          return new QueryDemoResult(
              "jpql",
              bookings.size(),
              count,
              queryCount,
              "BookingRepository.findByUsername + countByHotelAndDateRange (@Query JPQL)",
              sampleBookingSummaries(bookings));
        });
  }

  public QueryDemoResult queryCriteria() {
    LabFixtures fixtures = prepareWithSamples();
    return readTx.execute(
        status -> {
          BookingSearchCriteria criteria =
              new BookingSearchCriteria(
                  fixtures.user().getId(),
                  fixtures.hotel().getId(),
                  BookingStatus.CONFIRMED,
                  null,
                  null);

          Statistics stats = beginStats();
          List<Booking> bookings = bookingCriteriaRepository.search(criteria);
          long queryCount = stats.getPrepareStatementCount();
          endStats(stats);

          return new QueryDemoResult(
              "criteria",
              bookings.size(),
              bookings.size(),
              queryCount,
              "BookingCriteriaRepository.search via CriteriaBuilder",
              sampleBookingSummaries(bookings));
        });
  }

  public QueryDemoResult querySpec() {
    LabFixtures fixtures = prepareWithSamples();
    return readTx.execute(
        status -> {
          Specification<Booking> specification =
              Specification.where(BookingSpec.forUser(fixtures.user().getId()))
                  .and(BookingSpec.forHotel(fixtures.hotel().getId()))
                  .and(BookingSpec.hasStatus(BookingStatus.CONFIRMED));

          Statistics stats = beginStats();
          var page = bookingRepository.findAll(specification, PageRequest.of(0, 20));
          long queryCount = stats.getPrepareStatementCount();
          endStats(stats);

          return new QueryDemoResult(
              "specifications",
              page.getNumberOfElements(),
              page.getTotalElements(),
              queryCount,
              "BookingSpec composition (same filters the REST list endpoint uses)",
              sampleBookingSummaries(page.getContent()));
        });
  }

  public BookingTxResult bookingSuccess() {
    LabFixtures fixtures = writeTx.execute(status -> ensureFixtures(true));
    LocalDate checkIn = nextOpenNight(fixtures.hotel().getId());
    LocalDate checkOut = checkIn.plusDays(1);
    writeTx.executeWithoutResult(status -> ensureInventory(fixtures.hotel().getId(), checkIn, 2));

    long bookingsBefore = bookingRepository.count();
    long paymentsBefore = paymentRepository.count();
    int roomsBefore = availableRooms(fixtures.hotel().getId(), checkIn);

    Booking booking =
        bookingService.createBooking(
            fixtures.user().getUsername(),
            new CreateBookingRequest(
                fixtures.hotel().getId(), LAB_ROOM_TYPE, checkIn, checkOut, "approve"));

    return new BookingTxResult(
        true,
        booking.getId(),
        booking.getStatus().name(),
        checkIn.toString(),
        checkOut.toString(),
        bookingsBefore,
        bookingRepository.count(),
        paymentsBefore,
        paymentRepository.count(),
        roomsBefore,
        availableRooms(fixtures.hotel().getId(), checkIn),
        "BEGIN → reserve → PENDING → charge → CONFIRMED → COMMIT; notification AFTER_COMMIT",
        null);
  }

  public BookingTxResult bookingRollback() {
    LabFixtures fixtures = writeTx.execute(status -> ensureFixtures(true));
    LocalDate checkIn = nextOpenNight(fixtures.hotel().getId());
    LocalDate checkOut = checkIn.plusDays(1);
    writeTx.executeWithoutResult(status -> ensureInventory(fixtures.hotel().getId(), checkIn, 2));

    long bookingsBefore = bookingRepository.count();
    long paymentsBefore = paymentRepository.count();
    int roomsBefore = availableRooms(fixtures.hotel().getId(), checkIn);

    String error = null;
    try {
      bookingService.createBooking(
          fixtures.user().getUsername(),
          new CreateBookingRequest(
              fixtures.hotel().getId(), LAB_ROOM_TYPE, checkIn, checkOut, "decline"));
    } catch (PaymentFailedException ex) {
      error = ex.getMessage();
    } catch (RuntimeException ex) {
      error = ex.getMessage();
    }

    return new BookingTxResult(
        false,
        null,
        null,
        checkIn.toString(),
        checkOut.toString(),
        bookingsBefore,
        bookingRepository.count(),
        paymentsBefore,
        paymentRepository.count(),
        roomsBefore,
        availableRooms(fixtures.hotel().getId(), checkIn),
        "paymentToken=decline → PaymentFailedException → full TX rollback (rollbackFor)",
        error);
  }

  public PropagationMapResult propagationMap() throws NoSuchMethodException {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put(
        "BookingService.createBooking",
        describeTransactional(
            BookingService.class.getMethod(
                "createBooking", String.class, CreateBookingRequest.class)));
    entries.put(
        "PaymentService.charge",
        describeTransactional(
            PaymentService.class.getMethod("charge", Booking.class, String.class)));
    entries.put(
        "NotificationService.onBookingConfirmed",
        describeTransactional(
                NotificationService.class.getMethod(
                    "onBookingConfirmed", com.voyage.app.booking.BookingConfirmedEvent.class))
            + " + @TransactionalEventListener(AFTER_COMMIT)");
    entries.put(
        "NotificationWriter.writeBookingConfirmed",
        describeTransactional(
            NotificationWriter.class.getMethod(
                "writeBookingConfirmed", com.voyage.app.booking.BookingConfirmedEvent.class)));
    entries.put(
        "BookingService.inspectStatus",
        describeTransactional(BookingService.class.getMethod("inspectStatus", Long.class)));
    return new PropagationMapResult(
        entries,
        "Payment joins booking TX (REQUIRED). Notification runs after commit (NOT_SUPPORTED) then REQUIRES_NEW write.");
  }

  public LockContentionResult lockContention() {
    LabFixtures fixtures = writeTx.execute(status -> ensureFixtures(true));
    LocalDate stayDate = nextOpenNight(fixtures.hotel().getId()).plusDays(14);
    writeTx.executeWithoutResult(status -> ensureInventory(fixtures.hotel().getId(), stayDate, 3));

    TransactionTemplate holdTx = new TransactionTemplate(transactionManager);
    TransactionTemplate reserveTx = new TransactionTemplate(transactionManager);
    AtomicReference<String> holderNote = new AtomicReference<>("hold not started");
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Long> holdFuture =
          executor.submit(
              () ->
                  holdTx.execute(
                      status -> {
                        RoomInventory locked =
                            roomInventoryRepository
                                .findForUpdate(fixtures.hotel().getId(), stayDate, LAB_ROOM_TYPE)
                                .orElseThrow();
                        holderNote.set("holding FOR UPDATE on inventory id=" + locked.getId());
                        sleep(LOCK_HOLD_MS);
                        return locked.getId();
                      }));

      sleep(200);

      Instant started = Instant.now();
      Future<String> reserveFuture =
          executor.submit(
              (Callable<String>)
                  () ->
                      reserveTx.execute(
                          status -> {
                            inventoryService.reserveRoom(
                                fixtures.hotel().getId(), LAB_ROOM_TYPE, stayDate);
                            return "reserved after wait/retry";
                          }));

      Long inventoryId = holdFuture.get(10, TimeUnit.SECONDS);
      String reserveResult = reserveFuture.get(10, TimeUnit.SECONDS);
      long elapsedMs = Instant.now().toEpochMilli() - started.toEpochMilli();

      return new LockContentionResult(
          inventoryId,
          stayDate.toString(),
          holderNote.get(),
          reserveResult,
          elapsedMs,
          availableRooms(fixtures.hotel().getId(), stayDate),
          "Second caller blocked on FOR UPDATE; InventoryService @Retryable covers lock failures under contention.");
    } catch (Exception ex) {
      throw new IllegalStateException("Lock contention demo failed: " + ex.getMessage(), ex);
    } finally {
      executor.shutdownNow();
    }
  }

  private LabFixtures prepareWithSamples() {
    LabFixtures fixtures = writeTx.execute(status -> ensureFixtures(true));
    ensureSampleBookings(fixtures);
    return fixtures;
  }

  private LabFixtures ensureFixtures(boolean withFreshInventoryWindow) {
    User user =
        userRepository
            .findByUsername(LAB_USER)
            .orElseGet(
                () ->
                    userRepository.save(
                        new User(
                            LAB_USER,
                            LAB_USER + "@voyage.local",
                            passwordEncoder.encode("password123"),
                            Role.CUSTOMER)));

    Hotel hotel =
        hotelRepository.findAll().stream()
            .filter(h -> LAB_HOTEL_NAME.equals(h.getName()))
            .findFirst()
            .orElseGet(() -> hotelRepository.save(new Hotel(LAB_HOTEL_NAME, "Lab City", 150.0)));

    LocalDate checkIn = LocalDate.now().plusDays(30);
    LocalDate checkOut = checkIn.plusDays(1);
    if (withFreshInventoryWindow) {
      ensureInventory(hotel.getId(), checkIn, 5);
      ensureInventory(hotel.getId(), checkIn.plusDays(1), 5);
    }
    return new LabFixtures(hotel, user, checkIn, checkOut);
  }

  private void ensureSampleBookings(LabFixtures fixtures) {
    int existing =
        bookingRepository
            .findByHotelAndStatus(fixtures.hotel().getId(), BookingStatus.CONFIRMED)
            .size();
    if (existing >= SAMPLE_BOOKINGS) {
      return;
    }
    int needed = SAMPLE_BOOKINGS - existing;
    for (int i = 0; i < needed; i++) {
      LocalDate checkIn = fixtures.checkIn().plusDays(2L + existing + i);
      LocalDate checkOut = checkIn.plusDays(1);
      writeTx.executeWithoutResult(status -> ensureInventory(fixtures.hotel().getId(), checkIn, 2));
      // Own TX boundary via BookingService proxy (do not nest inside writeTx)
      bookingService.createBooking(
          fixtures.user().getUsername(),
          new CreateBookingRequest(
              fixtures.hotel().getId(), LAB_ROOM_TYPE, checkIn, checkOut, "approve"));
    }
  }

  private void ensureInventory(Long hotelId, LocalDate date, int minAvailable) {
    roomInventoryRepository
        .findByHotelIdAndDateAndRoomType(hotelId, date, LAB_ROOM_TYPE)
        .ifPresentOrElse(
            inventory -> {
              if (inventory.getAvailableRooms() < minAvailable) {
                inventory.setAvailableRooms(minAvailable);
                roomInventoryRepository.save(inventory);
              }
            },
            () -> inventoryService.createInventory(hotelId, LAB_ROOM_TYPE, date, minAvailable));
  }

  private LocalDate nextOpenNight(Long hotelId) {
    LocalDate date = LocalDate.now().plusDays(40);
    for (int i = 0; i < 60; i++) {
      LocalDate candidate = date.plusDays(i);
      var existing =
          roomInventoryRepository.findByHotelIdAndDateAndRoomType(
              hotelId, candidate, LAB_ROOM_TYPE);
      if (existing.isEmpty() || existing.get().getAvailableRooms() > 0) {
        return candidate;
      }
    }
    return date.plusDays(90);
  }

  private int availableRooms(Long hotelId, LocalDate date) {
    return roomInventoryRepository
        .findByHotelIdAndDateAndRoomType(hotelId, date, LAB_ROOM_TYPE)
        .map(RoomInventory::getAvailableRooms)
        .orElse(0);
  }

  private List<BookingSummary> sampleBookingSummaries(List<Booking> bookings) {
    return bookings.stream()
        .limit(5)
        .map(
            b ->
                new BookingSummary(
                    b.getId(),
                    b.getStatus().name(),
                    b.getCheckIn().toString(),
                    b.getCheckOut().toString()))
        .toList();
  }

  private Statistics beginStats() {
    Session session = entityManager.unwrap(Session.class);
    Statistics stats = session.getSessionFactory().getStatistics();
    stats.clear();
    stats.setStatisticsEnabled(true);
    return stats;
  }

  private void endStats(Statistics stats) {
    stats.setStatisticsEnabled(false);
  }

  private static String describeTransactional(Method method) {
    Transactional transactional = method.getAnnotation(Transactional.class);
    if (transactional == null) {
      return "no @Transactional";
    }
    Propagation propagation = transactional.propagation();
    String isolation = transactional.isolation().name();
    String rollback =
        transactional.rollbackFor().length == 0
            ? "default (RuntimeException)"
            : transactional.rollbackFor()[0].getSimpleName();
    return "propagation=" + propagation + ", isolation=" + isolation + ", rollbackFor=" + rollback;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during lab demo", ex);
    }
  }

  private record LabFixtures(Hotel hotel, User user, LocalDate checkIn, LocalDate checkOut) {}
}

record LabSeedResult(
    Long hotelId,
    String username,
    String checkIn,
    String checkOut,
    long bookingCount,
    int availableRoomsOnCheckIn,
    String tip) {}

record LifecyclePersistResult(
    Long hotelId, String name, String beforePersist, String afterPersist, String tip) {}

record LifecycleDetachResult(
    Long hotelId,
    String originalName,
    String attemptedName,
    String nameAfterReload,
    boolean mutationPersisted,
    String tip) {}

record LoadingDemoResult(
    String scenario,
    int bookingCount,
    long queryCount,
    List<String> hotelNamesTouched,
    String observation,
    String tip) {}

record QueryDemoResult(
    String api,
    int resultCount,
    long totalOrCount,
    long queryCount,
    String observation,
    List<BookingSummary> sample) {}

record BookingSummary(Long id, String status, String checkIn, String checkOut) {}

record BookingTxResult(
    boolean success,
    Long bookingId,
    String status,
    String checkIn,
    String checkOut,
    long bookingsBefore,
    long bookingsAfter,
    long paymentsBefore,
    long paymentsAfter,
    int roomsBefore,
    int roomsAfter,
    String observation,
    String error) {}

record PropagationMapResult(Map<String, String> methods, String tip) {}
