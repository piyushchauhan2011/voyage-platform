package com.voyage.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotelSearchSeederTest {

  @Mock private HotelRepository hotelRepository;

  @InjectMocks private HotelSearchSeeder seeder;

  @Test
  void seedCreatesRequestedCountWhenEmpty() {
    when(hotelRepository.findAll()).thenReturn(List.of());
    when(hotelRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              List<Hotel> hotels = invocation.getArgument(0);
              hotels.forEach(hotel -> hotel.setId(1L));
              return hotels;
            });
    when(hotelRepository.count()).thenReturn(25L);

    HotelSearchSeeder.SeedResult result = seeder.seed(25);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Hotel>> captor = ArgumentCaptor.forClass(List.class);
    verify(hotelRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(25);
    Hotel first = captor.getValue().getFirst();
    assertThat(first.getNameTh()).isNotBlank();
    assertThat(first.getCityTh()).isNotBlank();
    assertThat(first.getImageUrl()).contains("picsum.photos");
    assertThat(first.getStarRating()).isBetween(3, 5);
    assertThat(first.getAddress()).isNotBlank();
    assertThat(first.getAddressTh()).isNotBlank();
    assertThat(result.created()).isEqualTo(25);
    assertThat(result.totalHotels()).isEqualTo(25L);
  }
}
