package com.voyage.app.hotel;

import com.voyage.app.kafka.HotelEventPublisher;
import com.voyage.app.kafka.HotelEventType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Service marks this class as a Spring-managed bean (a specialisation of @Component).
 *
 * Constructor injection is preferred over @Autowired field injection because:
 *   1. The dependency is immutable (final field).
 *   2. Easier to unit-test — you can pass a mock in the constructor without a Spring context.
 *   3. Makes missing dependencies a compile error, not a runtime NPE.
 */
@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final HotelEventPublisher hotelEventPublisher;

    public HotelService(HotelRepository hotelRepository, ObjectProvider<HotelEventPublisher> hotelEventPublisherProvider) {
        this.hotelRepository = hotelRepository;
        this.hotelEventPublisher = hotelEventPublisherProvider.getIfAvailable();
    }

    public List<Hotel> findAll() {
        return hotelRepository.findAll();
    }

    public Hotel findById(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));
    }

    public Hotel save(Hotel hotel) {
        Hotel savedHotel = hotelRepository.save(hotel);
        publishEvent(HotelEventType.CREATED, savedHotel);
        return savedHotel;
    }

    public List<Hotel> findByCity(String city) {
        return hotelRepository.findByCity(city);
    }

    public Hotel update(Long id, Hotel updates) {
        Hotel hotel = findById(id);
        hotel.setName(updates.getName());
        hotel.setCity(updates.getCity());
        hotel.setPricePerNight(updates.getPricePerNight());
        Hotel updatedHotel = hotelRepository.save(hotel);
        publishEvent(HotelEventType.UPDATED, updatedHotel);
        return updatedHotel;
    }

    public void delete(Long id) {
        Hotel hotel = findById(id);
        hotelRepository.deleteById(id);
        publishEvent(HotelEventType.DELETED, hotel);
    }

    private void publishEvent(HotelEventType eventType, Hotel hotel) {
        if (hotelEventPublisher != null) {
            hotelEventPublisher.publish(eventType, hotel);
        }
    }
}
