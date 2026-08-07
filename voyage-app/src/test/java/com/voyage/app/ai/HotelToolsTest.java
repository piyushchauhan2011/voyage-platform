package com.voyage.app.ai;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.inventory.RoomInventory;
import com.voyage.app.inventory.RoomInventoryRepository;
import com.voyage.app.inventory.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the tools the model is allowed to call, without involving a model.
 *
 * These are ordinary Java methods, and their correctness is entirely our responsibility —
 * if searchHotels quietly ignores maxPrice, the assistant will confidently recommend hotels
 * over budget and the prompt will get the blame.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HotelToolsTest {

    /**
     * The context is shared with other tests that leave hotels and bookings behind, so this
     * test works inside its own city rather than clearing the tables. @Transactional rolls the
     * fixtures back afterwards.
     */
    private static final String CITY = "Toolsville";
    private static final String MOUNTAIN_CITY = "Toolsville Alps";

    @Autowired HotelTools hotelTools;
    @Autowired HotelRepository hotelRepository;
    @Autowired RoomInventoryRepository roomInventoryRepository;

    private Hotel cheapBeachHotel;

    @BeforeEach
    void setUp() {
        hotelTools.resetInvocations();

        cheapBeachHotel = hotelRepository.save(new Hotel("Driftwood Rooms", CITY, 74.0,
                "Budget rooms a short barefoot walk from a wide stretch of sand.", "free wifi"));
        hotelRepository.save(new Hotel("Azure Sands Resort", CITY, 320.0,
                "Large shorefront resort with private access to the sand.", "spa, pools"));
        hotelRepository.save(new Hotel("Alpine Ridge Chalet", MOUNTAIN_CITY, 145.0,
                "Timber chalet at the base of the ski lifts.", "sauna"));
    }

    @Test
    void searchHotels_appliesMaxPrice() {
        List<HotelTools.HotelSummary> results = hotelTools.searchHotels(CITY, null, 100.0);

        assertThat(results).extracting(HotelTools.HotelSummary::name)
                .containsExactly("Driftwood Rooms");
    }

    @Test
    void searchHotels_appliesMinPrice() {
        List<HotelTools.HotelSummary> results = hotelTools.searchHotels(CITY, 100.0, null);

        assertThat(results).extracting(HotelTools.HotelSummary::name)
                .containsExactly("Azure Sands Resort");
    }

    @Test
    void searchHotels_appliesCityFilter() {
        List<HotelTools.HotelSummary> results = hotelTools.searchHotels(MOUNTAIN_CITY, null, null);

        assertThat(results).extracting(HotelTools.HotelSummary::name)
                .containsExactly("Alpine Ridge Chalet");
    }

    @Test
    void searchHotels_treatsBlankCityAsNoFilter() {
        // The model sometimes sends "" rather than omitting an optional argument. A blank city
        // must mean "all cities", not "the city whose name is empty" — which would return nothing.
        List<HotelTools.HotelSummary> blankCity = hotelTools.searchHotels("  ", null, null);
        List<HotelTools.HotelSummary> noCity = hotelTools.searchHotels(null, null, null);

        // Sizes rather than contents: the sort is by price alone, so ties order unpredictably.
        assertThat(blankCity).isNotEmpty().hasSameSizeAs(noCity);
    }

    @Test
    void searchHotels_returnsCheapestFirst() {
        List<HotelTools.HotelSummary> results = hotelTools.searchHotels(CITY, null, null);

        assertThat(results).extracting(HotelTools.HotelSummary::pricePerNight)
                .containsExactly(74.0, 320.0);
    }

    @Test
    void searchHotels_recordsInvocationForTheTrace() {
        hotelTools.searchHotels(CITY, null, 100.0);

        assertThat(hotelTools.drainInvocations())
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.tool()).isEqualTo("searchHotels");
                    assertThat(invocation.resultCount()).isEqualTo(1);
                });
    }

    @Test
    void getHotelDetails_returnsDescriptionAndAmenities() {
        HotelTools.HotelDetails details = hotelTools.getHotelDetails(cheapBeachHotel.getId());

        assertThat(details.name()).isEqualTo("Driftwood Rooms");
        assertThat(details.description()).contains("stretch of sand");
        assertThat(details.amenities()).isEqualTo("free wifi");
    }

    @Test
    void checkAvailability_reportsAvailableWhenEveryNightHasRooms() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        seedInventory(cheapBeachHotel, checkIn, 3, 2);

        HotelTools.AvailabilityReport report = hotelTools.checkAvailability(
                cheapBeachHotel.getId(), checkIn.toString(), checkIn.plusDays(3).toString(), "DOUBLE");

        assertThat(report.availableForWholeStay()).isTrue();
        assertThat(report.nights()).hasSize(3);
    }

    @Test
    void checkAvailability_reportsUnavailableWhenANightIsSoldOut() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        seedInventory(cheapBeachHotel, checkIn, 3, 2);
        // Sell out the middle night — one gap is enough to break a continuous stay.
        RoomInventory soldOut = roomInventoryRepository
                .findByHotelIdAndDateAndRoomType(cheapBeachHotel.getId(), checkIn.plusDays(1), RoomType.DOUBLE)
                .orElseThrow();
        soldOut.setAvailableRooms(0);
        roomInventoryRepository.save(soldOut);

        HotelTools.AvailabilityReport report = hotelTools.checkAvailability(
                cheapBeachHotel.getId(), checkIn.toString(), checkIn.plusDays(3).toString(), "DOUBLE");

        assertThat(report.availableForWholeStay()).isFalse();
    }

    @Test
    void checkAvailability_excludesCheckOutNight() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        seedInventory(cheapBeachHotel, checkIn, 3, 2);

        HotelTools.AvailabilityReport report = hotelTools.checkAvailability(
                cheapBeachHotel.getId(), checkIn.toString(), checkIn.plusDays(1).toString(), "DOUBLE");

        // A one-night stay occupies only the check-in date.
        assertThat(report.nights()).hasSize(1);
        assertThat(report.nights().getFirst().date()).isEqualTo(checkIn.toString());
    }

    @Test
    void checkAvailability_rejectsMalformedDates() {
        assertThatThrownBy(() -> hotelTools.checkAvailability(
                cheapBeachHotel.getId(), "next tuesday", "2026-01-05", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void checkAvailability_rejectsCheckOutBeforeCheckIn() {
        assertThatThrownBy(() -> hotelTools.checkAvailability(
                cheapBeachHotel.getId(), "2026-05-10", "2026-05-08", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be after");
    }

    @Test
    void checkAvailability_rejectsUnknownRoomType() {
        assertThatThrownBy(() -> hotelTools.checkAvailability(
                cheapBeachHotel.getId(), "2026-05-10", "2026-05-12", "PENTHOUSE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SINGLE, DOUBLE, SUITE");
    }

    private void seedInventory(Hotel hotel, LocalDate from, int nights, int roomsPerNight) {
        for (int day = 0; day < nights; day++) {
            roomInventoryRepository.save(
                    new RoomInventory(hotel, RoomType.DOUBLE, from.plusDays(day), roomsPerNight));
        }
    }
}
