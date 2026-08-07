package com.voyage.app.ai;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelService;
import com.voyage.app.inventory.RoomInventory;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rung 6 — the functions Gemini is allowed to call.
 *
 * Tool calling is what turns a chat model into something that can answer questions about live
 * data. The model never touches the database: it emits a structured request naming a tool and
 * its arguments, Spring AI invokes the matching Java method, and the return value is fed back
 * into the conversation.
 *
 * The @Tool descriptions are not documentation, they are the API the model programs against.
 * A vague description is the single most common reason a model picks the wrong tool or invents
 * arguments, so they are written for that reader.
 *
 * These methods delegate to the existing HotelService and RoomInventoryRepository, which means
 * caching, specifications, and validation from earlier phases all still apply.
 */
@Component
public class HotelTools {

    /** Hard ceiling on rows returned to the model — every row costs prompt tokens. */
    private static final int MAX_RESULTS = 10;

    private final HotelService hotelService;
    private final RoomInventoryRepository roomInventoryRepository;

    /**
     * Records which tools ran, so the lab UI can show the model's decisions.
     * Thread-safe and explicitly cleared per request by the assistant.
     */
    private final List<ToolInvocation> invocations = new CopyOnWriteArrayList<>();

    public HotelTools(HotelService hotelService, RoomInventoryRepository roomInventoryRepository) {
        this.hotelService = hotelService;
        this.roomInventoryRepository = roomInventoryRepository;
    }

    @Tool(description = """
            Search the Voyage hotel database by city and/or nightly price range.
            Use this whenever the user mentions a budget, a price limit, or a specific city,
            because prices in the database are authoritative and must not be guessed.
            Returns hotels with their real current nightly price.
            """)
    public List<HotelSummary> searchHotels(
            @ToolParam(required = false, description = "City name, e.g. Lisbon. Omit to search all cities.")
            String city,
            @ToolParam(required = false, description = "Minimum nightly price in USD.")
            Double minPrice,
            @ToolParam(required = false, description = "Maximum nightly price in USD, e.g. 100 for 'under $100'.")
            Double maxPrice) {

        var pageable = PageRequest.of(0, MAX_RESULTS, Sort.by("pricePerNight"));
        List<HotelSummary> results = hotelService.findAll(blankToNull(city), minPrice, maxPrice, pageable)
                .getContent()
                .stream()
                .map(HotelSummary::from)
                .toList();

        record Args(String city, Double minPrice, Double maxPrice) {
        }
        invocations.add(new ToolInvocation("searchHotels", new Args(city, minPrice, maxPrice), results.size()));
        return results;
    }

    @Tool(description = """
            Fetch the full details of one hotel by its numeric id, including its description
            and amenities. Use this after a search when the user asks about a specific hotel.
            """)
    public HotelDetails getHotelDetails(
            @ToolParam(description = "The hotel's numeric id, as returned by searchHotels.")
            Long hotelId) {
        Hotel hotel = hotelService.findById(hotelId);
        invocations.add(new ToolInvocation("getHotelDetails", hotelId, 1));
        return HotelDetails.from(hotel);
    }

    @Tool(description = """
            Check how many rooms are still available at a hotel for a date range.
            Use this whenever the user names travel dates or asks whether somewhere is bookable.
            Dates must be ISO format (YYYY-MM-DD). A hotel is only bookable if every night in
            the range has at least one room free.
            """)
    public AvailabilityReport checkAvailability(
            @ToolParam(description = "The hotel's numeric id.")
            Long hotelId,
            @ToolParam(description = "Check-in date in YYYY-MM-DD format.")
            String checkInDate,
            @ToolParam(description = "Check-out date in YYYY-MM-DD format. Must be after check-in.")
            String checkOutDate,
            @ToolParam(required = false, description = "Room type: SINGLE, DOUBLE or SUITE. Omit for any type.")
            String roomType) {

        LocalDate checkIn = parseDate(checkInDate, "checkInDate");
        LocalDate checkOut = parseDate(checkOutDate, "checkOutDate");
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }

        RoomType type = parseRoomType(roomType);
        // The final night is the day before check-out, so the window is inclusive of checkIn only.
        List<RoomInventory> window =
                roomInventoryRepository.findInventoryWindow(hotelId, checkIn, checkOut.minusDays(1), type);

        List<NightAvailability> nights = window.stream()
                .map(row -> new NightAvailability(row.getDate().toString(), row.getRoomType().name(), row.getAvailableRooms()))
                .toList();

        long nightsRequested = checkIn.datesUntil(checkOut).count();
        long nightsCovered = nights.stream()
                .filter(night -> night.availableRooms() > 0)
                .map(NightAvailability::date)
                .distinct()
                .count();
        boolean available = nightsCovered == nightsRequested;

        record Args(Long hotelId, String checkInDate, String checkOutDate, String roomType) {
        }
        invocations.add(new ToolInvocation("checkAvailability",
                new Args(hotelId, checkInDate, checkOutDate, roomType), nights.size()));

        return new AvailabilityReport(hotelId, checkInDate, checkOutDate, available, nights);
    }

    // ------------------------------------------------------------------
    // Invocation trace — read by the assistant to show what the model chose to call
    // ------------------------------------------------------------------

    void resetInvocations() {
        invocations.clear();
    }

    List<ToolInvocation> drainInvocations() {
        List<ToolInvocation> snapshot = new ArrayList<>(invocations);
        invocations.clear();
        return snapshot;
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException(field + " must be a date in YYYY-MM-DD format, got: " + value);
        }
    }

    private static RoomType parseRoomType(String roomType) {
        if (roomType == null || roomType.isBlank()) {
            return null;
        }
        try {
            return RoomType.valueOf(roomType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("roomType must be one of SINGLE, DOUBLE, SUITE — got: " + roomType);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record HotelSummary(Long id, String name, String city, Double pricePerNight) {

        static HotelSummary from(Hotel hotel) {
            return new HotelSummary(hotel.getId(), hotel.getName(), hotel.getCity(), hotel.getPricePerNight());
        }
    }

    public record HotelDetails(Long id, String name, String city, Double pricePerNight,
                               String description, String amenities) {

        static HotelDetails from(Hotel hotel) {
            return new HotelDetails(hotel.getId(), hotel.getName(), hotel.getCity(),
                    hotel.getPricePerNight(), hotel.getDescription(), hotel.getAmenities());
        }
    }

    public record NightAvailability(String date, String roomType, int availableRooms) {
    }

    public record AvailabilityReport(Long hotelId, String checkInDate, String checkOutDate,
                                     boolean availableForWholeStay, List<NightAvailability> nights) {
    }

    public record ToolInvocation(String tool, Object arguments, int resultCount) {
    }
}
