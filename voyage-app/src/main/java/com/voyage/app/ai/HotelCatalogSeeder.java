package com.voyage.app.ai;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.RoomInventory;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds a hotel catalogue rich enough for semantic search to be interesting.
 *
 * The app runs with ddl-auto=create-drop, so the database is empty after every restart.
 * Rather than a startup hook, seeding is an explicit lab step: you press the button,
 * you see what got created, and re-pressing it is safe.
 *
 * The descriptions are deliberately written so that the obvious query ("near the beach")
 * matches hotels whose name and city never contain the word "beach" — that is the whole
 * point of embedding search versus a SQL LIKE.
 */
@Service
public class HotelCatalogSeeder {

    /** Days of room inventory generated per hotel, starting today. */
    private static final int INVENTORY_WINDOW_DAYS = 30;

    private final HotelRepository hotelRepository;
    private final RoomInventoryRepository roomInventoryRepository;

    public HotelCatalogSeeder(HotelRepository hotelRepository, RoomInventoryRepository roomInventoryRepository) {
        this.hotelRepository = hotelRepository;
        this.roomInventoryRepository = roomInventoryRepository;
    }

    @Transactional
    public SeedResult seed() {
        List<Hotel> catalog = catalog();

        List<String> existingNames = hotelRepository.findAll().stream().map(Hotel::getName).toList();
        List<Hotel> toCreate = catalog.stream()
                .filter(hotel -> !existingNames.contains(hotel.getName()))
                .toList();

        List<Hotel> created = hotelRepository.saveAll(toCreate);
        int inventoryRows = seedInventory(created);

        return new SeedResult(
                created.size(),
                catalog.size() - created.size(),
                hotelRepository.count(),
                inventoryRows,
                created.stream().map(Hotel::getName).toList(),
                "Hotels are stored in Postgres as ordinary rows. Nothing is searchable by meaning yet — "
                        + "run Ingest next to embed each description into pgvector.",
                "Descriptions mention landmarks like 'sand', 'surf', and 'ski lifts' without repeating the city name, "
                        + "so semantic search has something to prove that a SQL LIKE could not."
        );
    }

    /**
     * Gives every seeded hotel a rolling window of availability so the
     * checkAvailability tool has real rows to read instead of always answering "unknown".
     */
    private int seedInventory(List<Hotel> hotels) {
        if (hotels.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        List<RoomInventory> rows = new ArrayList<>();
        for (Hotel hotel : hotels) {
            for (int day = 0; day < INVENTORY_WINDOW_DAYS; day++) {
                LocalDate date = today.plusDays(day);
                rows.add(new RoomInventory(hotel, RoomType.SINGLE, date, 4));
                rows.add(new RoomInventory(hotel, RoomType.DOUBLE, date, 6));
                rows.add(new RoomInventory(hotel, RoomType.SUITE, date, 2));
            }
        }
        return roomInventoryRepository.saveAll(rows).size();
    }

    private List<Hotel> catalog() {
        return List.of(
                // --- Beachfront, cheap: the target of "find hotels near beach under $100" ---
                new Hotel("Salt & Pine Guesthouse", "Lisbon", 82.0,
                        "A small guesthouse two minutes on foot from the sand, where the sound of surf carries "
                                + "into the courtyard at night. Boards and towels are lent out free to guests.",
                        "free wifi, surfboard rental, breakfast, shared kitchen"),
                new Hotel("Driftwood Rooms", "Lisbon", 74.0,
                        "Budget rooms above a seafood cafe, a short barefoot walk from a wide stretch of sand. "
                                + "Popular with surfers who want an early start on the waves.",
                        "free wifi, board storage, laundry"),
                new Hotel("The Tideline Inn", "Nice", 96.0,
                        "Sits directly on the shorefront promenade with the water visible from every upper room. "
                                + "Sun loungers and umbrellas are set out on the pebbles each morning.",
                        "sea view, free wifi, loungers, bar"),
                new Hotel("Coral Court", "Barcelona", 91.0,
                        "Pastel rooms one block back from the shore, close enough that most guests never touch "
                                + "public transport. The rooftop looks straight out over the water.",
                        "rooftop terrace, free wifi, air conditioning"),
                new Hotel("Harbour Light Lodge", "Nice", 88.0,
                        "A converted fisherman's cottage beside the old harbour, steps from a quiet cove that "
                                + "stays calm even in late summer.",
                        "free wifi, breakfast, bicycle hire"),

                // --- Beachfront, expensive: correct semantic match, should fail the price filter ---
                new Hotel("Azure Sands Resort", "Nice", 320.0,
                        "A large shorefront resort with private access to the sand, three pools, and a spa "
                                + "that looks out over the water.",
                        "private beach, spa, 3 pools, fine dining, gym"),
                new Hotel("The Blue Horizon", "Barcelona", 265.0,
                        "Designer suites on the waterfront with floor-to-ceiling glass facing the sea and a "
                                + "restaurant built out over the water on stilts.",
                        "sea view, spa, restaurant, valet parking"),

                // --- Mountain ---
                new Hotel("Alpine Ridge Chalet", "Innsbruck", 145.0,
                        "A timber chalet at the base of the ski lifts, with a drying room for boots and a "
                                + "wood-fired lounge for after the last run.",
                        "ski storage, sauna, fireplace, free parking"),
                new Hotel("Pinecrest Lodge", "Innsbruck", 98.0,
                        "Simple rooms high on the valley wall, surrounded by hiking trails that start at the "
                                + "front door and climb toward the summit.",
                        "free parking, breakfast, trail maps"),
                new Hotel("Summit View Hotel", "Zermatt", 210.0,
                        "Every room faces the peaks, and the terrace is a well-known spot for watching the "
                                + "last light hit the mountain face.",
                        "mountain view, spa, restaurant, ski shuttle"),
                new Hotel("Glacier Base Inn", "Zermatt", 118.0,
                        "A no-frills base for climbers and skiers, right at the cable car station, with an "
                                + "early breakfast served before first light.",
                        "ski storage, early breakfast, boot dryer"),

                // --- Business / city centre ---
                new Hotel("Meridian Business Tower", "Frankfurt", 189.0,
                        "A glass tower in the banking district with soundproofed desks in every room and "
                                + "meeting suites on the mezzanine.",
                        "meeting rooms, desk, gym, airport shuttle, fast wifi"),
                new Hotel("The Ledger Hotel", "Frankfurt", 156.0,
                        "Built for weekday travellers, a five-minute walk from the exhibition halls, with a "
                                + "24-hour business lounge and printing.",
                        "business lounge, printing, desk, gym"),
                new Hotel("Kensington Court", "London", 240.0,
                        "A quiet townhouse hotel near the museums, popular with consultants who want to walk "
                                + "to client offices in the centre.",
                        "desk, meeting rooms, concierge, fast wifi"),
                new Hotel("Canary Point Suites", "London", 198.0,
                        "Serviced apartments in the financial quarter with kitchenettes for longer stays and "
                                + "a co-working floor on level two.",
                        "kitchenette, co-working, gym, laundry"),

                // --- Budget / hostel ---
                new Hotel("The Wandering Fox Hostel", "Lisbon", 34.0,
                        "Bunks and a handful of private rooms in the old town, with a loud communal kitchen "
                                + "and walking tours leaving from the lobby each morning.",
                        "shared kitchen, lockers, walking tours, free wifi"),
                new Hotel("Backpack & Bunk", "Barcelona", 29.0,
                        "Cheapest beds in the district, five minutes from the metro, aimed squarely at "
                                + "travellers who only need somewhere to sleep.",
                        "lockers, shared bathroom, free wifi"),
                new Hotel("City Nest Budget Rooms", "London", 67.0,
                        "Compact rooms with everything stripped back to essentials, chosen for the location "
                                + "rather than the space.",
                        "free wifi, shared lounge"),

                // --- Countryside / spa ---
                new Hotel("Willowbrook Manor", "Cotswolds", 175.0,
                        "A stone manor house in open countryside, with walled gardens, a long gravel drive, "
                                + "and absolute quiet after dark.",
                        "gardens, restaurant, fireplace, free parking"),
                new Hotel("Thermal Springs Retreat", "Budapest", 132.0,
                        "Built around natural hot springs, with mineral pools of varying temperature and "
                                + "treatment rooms in the vaulted basement.",
                        "thermal pools, spa, massage, restaurant")
        );
    }

    public record SeedResult(
            int created,
            int alreadyPresent,
            long totalHotels,
            int inventoryRowsCreated,
            List<String> createdNames,
            String observation,
            String tip
    ) {
    }
}
