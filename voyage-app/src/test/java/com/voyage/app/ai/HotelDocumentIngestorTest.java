package com.voyage.app.ai;

import com.voyage.app.hotel.Hotel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Document construction decides what similarity search can ever match, so it is worth
 * asserting directly rather than only through an end-to-end search.
 */
class HotelDocumentIngestorTest {

    @Test
    void documentIdIsStableForAHotelSoReingestingReplacesRatherThanDuplicates() {
        assertThat(HotelDocumentIngestor.toDocument(hotel(7L)).getId())
                .isEqualTo(HotelDocumentIngestor.toDocument(hotel(7L)).getId());
    }

    @Test
    void differentHotelsGetDifferentDocumentIds() {
        assertThat(HotelDocumentIngestor.toDocument(hotel(7L)).getId())
                .isNotEqualTo(HotelDocumentIngestor.toDocument(hotel(8L)).getId());
    }

    @Test
    void documentIdIsAUuidBecausePgvectorKeysOnAUuidColumn() {
        String id = HotelDocumentIngestor.toDocument(hotel(7L)).getId();

        assertThat(java.util.UUID.fromString(id)).hasToString(id);
    }

    @Test
    void embeddedTextIncludesNameCityDescriptionAndAmenities() {
        Document document = HotelDocumentIngestor.toDocument(hotel(1L));

        assertThat(document.getText())
                .contains("Driftwood Rooms")
                .contains("Lisbon")
                .contains("stretch of sand")
                .contains("free wifi");
    }

    @Test
    void embeddedTextIncludesThePriceBecauseOnlyTheTextReachesThePrompt() {
        // Metadata is left behind by QuestionAnswerAdvisor, so a price kept only there is
        // invisible to the model and the RAG rung cannot answer a budget question.
        assertThat(HotelDocumentIngestor.toDocument(hotel(1L)).getText()).contains("74.0");
    }

    @Test
    void structuredFieldsGoToMetadataSoTheyCanBeFilteredExactly() {
        Document document = HotelDocumentIngestor.toDocument(hotel(1L));

        assertThat(document.getMetadata())
                .containsEntry("hotelId", 1L)
                .containsEntry("city", "Lisbon")
                .containsEntry("pricePerNight", 74.0);
    }

    @Test
    void missingAmenitiesDoNotProduceTheStringNull() {
        Hotel hotel = hotel(1L);
        hotel.setAmenities(null);

        assertThat(HotelDocumentIngestor.toDocument(hotel).getText())
                .contains("Amenities: not listed")
                .doesNotContain("null");
    }

    private static Hotel hotel(Long id) {
        Hotel hotel = new Hotel("Driftwood Rooms", "Lisbon", 74.0,
                "Budget rooms a short barefoot walk from a wide stretch of sand.", "free wifi");
        hotel.setId(id);
        return hotel;
    }
}
