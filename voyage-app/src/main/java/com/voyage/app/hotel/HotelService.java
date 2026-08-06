package com.voyage.app.hotel;

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

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    public List<Hotel> findAll() {
        return hotelRepository.findAll();
    }

    public Hotel findById(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));
    }

    public Hotel save(Hotel hotel) {
        return hotelRepository.save(hotel);
    }

    public List<Hotel> findByCity(String city) {
        return hotelRepository.findByCity(city);
    }

    public Hotel update(Long id, Hotel updates) {
        Hotel hotel = findById(id);
        hotel.setName(updates.getName());
        hotel.setCity(updates.getCity());
        hotel.setPricePerNight(updates.getPricePerNight());
        return hotelRepository.save(hotel);
    }

    public void delete(Long id) {
        if (!hotelRepository.existsById(id)) {
            throw new HotelNotFoundException(id);
        }
        hotelRepository.deleteById(id);
    }
}
