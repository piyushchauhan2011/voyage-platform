package com.voyage.app.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.voyage.app.hotel.Hotel;
import org.junit.jupiter.api.Test;

class HotelDocumentTest {

  @Test
  void fromCopiesBilingualAndSuggestFields() {
    Hotel hotel =
        new Hotel(
            "Palm Grove Hotel",
            "Phuket",
            120.0,
            "Near the beach",
            "wifi, pool",
            "โรงแรมดงปาล์ม",
            "ภูเก็ต",
            "ใกล้ชายหาด");
    hotel.setId(42L);
    hotel.setImageUrl("https://picsum.photos/seed/palm/800/500");
    hotel.setStarRating(4);
    hotel.setGuestRating(8.7);

    HotelDocument document = HotelDocument.from(hotel);

    assertThat(document.getId()).isEqualTo(42L);
    assertThat(document.getName()).isEqualTo("Palm Grove Hotel");
    assertThat(document.getNameTh()).isEqualTo("โรงแรมดงปาล์ม");
    assertThat(document.getCity()).isEqualTo("Phuket");
    assertThat(document.getCityTh()).isEqualTo("ภูเก็ต");
    assertThat(document.getDescriptionTh()).contains("ชายหาด");
    assertThat(document.getPricePerNight()).isEqualTo(120.0);
    assertThat(document.getNameSuggest()).isEqualTo("Palm Grove Hotel");
    assertThat(document.getNameThSuggest()).isEqualTo("โรงแรมดงปาล์ม");
    assertThat(document.getCitySuggest()).isEqualTo("Phuket");
    assertThat(document.getCityThSuggest()).isEqualTo("ภูเก็ต");
    assertThat(document.getImageUrl()).contains("picsum.photos");
    assertThat(document.getStarRating()).isEqualTo(4);
    assertThat(document.getGuestRating()).isEqualTo(8.7);
  }
}
