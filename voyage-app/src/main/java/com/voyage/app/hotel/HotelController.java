package com.voyage.app.hotel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @RestController = @Controller + @ResponseBody on every method.
 * Spring serialises the return value to JSON automatically (via Jackson).
 *
 * HTTP verb    annotation       typical status
 * GET          @GetMapping      200 OK
 * POST         @PostMapping     201 Created
 * PUT          @PutMapping      200 OK
 * DELETE       @DeleteMapping   204 No Content
 */
@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    // GET /api/hotels
    @GetMapping
    public List<Hotel> getAll() {
        return hotelService.findAll();
    }

    // GET /api/hotels/1
    @GetMapping("/{id}")
    public Hotel getById(@PathVariable Long id) {
        return hotelService.findById(id);
    }

    // GET /api/hotels/search?city=Paris
    @GetMapping("/search")
    public List<Hotel> getByCity(@RequestParam String city) {
        return hotelService.findByCity(city);
    }

    // POST /api/hotels   body: { "name": "Grand Hotel", "city": "Paris", "pricePerNight": 200 }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Hotel create(@RequestBody Hotel hotel) {
        return hotelService.save(hotel);
    }

    // PUT /api/hotels/1
    @PutMapping("/{id}")
    public Hotel update(@PathVariable Long id, @RequestBody Hotel hotel) {
        return hotelService.update(id, hotel);
    }

    // DELETE /api/hotels/1
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        hotelService.delete(id);
    }
}
