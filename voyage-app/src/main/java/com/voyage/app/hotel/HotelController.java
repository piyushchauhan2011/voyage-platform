package com.voyage.app.hotel;

import com.voyage.app.common.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * @RestController = @Controller + @ResponseBody on every method. Spring serialises the return value
 * to JSON automatically (via Jackson).
 *
 * <p>HTTP verb annotation typical status GET @GetMapping 200 OK POST @PostMapping 201 Created
 * PUT @PutMapping 200 OK DELETE @DeleteMapping 204 No Content
 */
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

  private final HotelService hotelService;

  public HotelController(HotelService hotelService) {
    this.hotelService = hotelService;
  }

  // GET /api/hotels
  @GetMapping
  public PageResponse<Hotel> getAll(
      @RequestParam(required = false) String city,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return PageResponse.from(hotelService.findAll(city, minPrice, maxPrice, pageable));
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
  public Hotel create(@Valid @RequestBody Hotel hotel) {
    return hotelService.save(hotel);
  }

  // PUT /api/hotels/1
  @PutMapping("/{id}")
  public Hotel update(@PathVariable Long id, @Valid @RequestBody Hotel hotel) {
    return hotelService.update(id, hotel);
  }

  /** Admin-only: assign manager and/or upgrade SaaS plan (ABAC attributes). */
  @PatchMapping("/{id}/management")
  public Hotel updateManagement(
      @PathVariable Long id, @Valid @RequestBody UpdateHotelManagementRequest request) {
    return hotelService.updateManagement(id, request);
  }

  // DELETE /api/hotels/1
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    hotelService.delete(id);
  }
}
