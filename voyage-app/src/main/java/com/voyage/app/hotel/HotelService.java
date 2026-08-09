package com.voyage.app.hotel;

import com.voyage.app.exception.ForbiddenException;
import com.voyage.app.exception.ResourceNotFoundException;
import com.voyage.app.kafka.HotelEventPublisher;
import com.voyage.app.kafka.HotelEventType;
import com.voyage.app.search.HotelIndexSync;
import com.voyage.app.security.HotelAccessService;
import com.voyage.app.user.Role;
import com.voyage.app.user.User;
import com.voyage.app.user.UserRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Service marks this class as a Spring-managed bean (a specialisation of @Component).
 *
 * <p>Constructor injection is preferred over @Autowired field injection because: 1. The dependency
 * is immutable (final field). 2. Easier to unit-test — you can pass a mock in the constructor
 * without a Spring context. 3. Makes missing dependencies a compile error, not a runtime NPE.
 */
@Service
public class HotelService {

  private final HotelRepository hotelRepository;
  private final HotelEventPublisher hotelEventPublisher;
  private final ObjectProvider<HotelIndexSync> hotelIndexSyncProvider;
  private final HotelAccessService hotelAccessService;
  private final UserRepository userRepository;

  public HotelService(
      HotelRepository hotelRepository,
      ObjectProvider<HotelEventPublisher> hotelEventPublisherProvider,
      ObjectProvider<HotelIndexSync> hotelIndexSyncProvider,
      HotelAccessService hotelAccessService,
      UserRepository userRepository) {
    this.hotelRepository = hotelRepository;
    this.hotelEventPublisher = hotelEventPublisherProvider.getIfAvailable();
    // Resolve lazily on write — do not force Elasticsearch beans during HotelService construction
    this.hotelIndexSyncProvider = hotelIndexSyncProvider;
    this.hotelAccessService = hotelAccessService;
    this.userRepository = userRepository;
  }

  @Cacheable(cacheNames = "hotelById", key = "#id")
  public Hotel findById(Long id) {
    return hotelRepository.findById(id).orElseThrow(() -> new HotelNotFoundException(id));
  }

  @Cacheable(cacheNames = "hotelsByCity", key = "#city")
  public List<Hotel> findByCity(String city) {
    return hotelRepository.findByCity(city);
  }

  public Page<Hotel> findAll(String city, Double minPrice, Double maxPrice, Pageable pageable) {
    Specification<Hotel> specification =
        Specification.where(HotelSpecifications.hasCity(city))
            .and(HotelSpecifications.priceAtLeast(minPrice))
            .and(HotelSpecifications.priceAtMost(maxPrice));
    return hotelRepository.findAll(specification, pageable);
  }

  public List<Hotel> findAll() {
    return hotelRepository.findAll();
  }

  @Transactional
  @Caching(
      put = @CachePut(cacheNames = "hotelById", key = "#result.id"),
      evict = @CacheEvict(cacheNames = "hotelsByCity", allEntries = true))
  public Hotel save(Hotel hotel) {
    User actor = hotelAccessService.requireCurrentUser();
    hotelAccessService.assertCanCreateHotel(actor);

    // Clients must not set privileged ABAC fields via the create body
    hotel.setSaasPlan(SaasPlan.FREE);
    if (actor.getRole() == Role.HOTEL_MANAGER) {
      hotel.setManager(actor);
    } else if (hotel.getManager() != null && hotel.getManager().getId() != null) {
      User manager =
          userRepository
              .findById(hotel.getManager().getId())
              .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
      if (manager.getRole() != Role.HOTEL_MANAGER && manager.getRole() != Role.ADMIN) {
        throw new ForbiddenException("Assigned manager must have HOTEL_MANAGER or ADMIN role");
      }
      hotel.setManager(manager);
    } else {
      hotel.setManager(null);
    }

    Hotel savedHotel = hotelRepository.save(hotel);
    publishEvent(HotelEventType.CREATED, savedHotel);
    indexUpsert(savedHotel);
    return savedHotel;
  }

  @Transactional
  @Caching(
      put = @CachePut(cacheNames = "hotelById", key = "#result.id"),
      evict = @CacheEvict(cacheNames = "hotelsByCity", allEntries = true))
  public Hotel update(Long id, Hotel updates) {
    hotelAccessService.assertCanManageHotel(id);
    Hotel hotel = findById(id);
    hotel.setName(updates.getName());
    hotel.setCity(updates.getCity());
    hotel.setPricePerNight(updates.getPricePerNight());
    hotel.setDescription(updates.getDescription());
    hotel.setAmenities(updates.getAmenities());
    hotel.setNameTh(updates.getNameTh());
    hotel.setCityTh(updates.getCityTh());
    hotel.setDescriptionTh(updates.getDescriptionTh());
    hotel.setImageUrl(updates.getImageUrl());
    hotel.setGalleryUrls(updates.getGalleryUrls());
    hotel.setStarRating(updates.getStarRating());
    hotel.setGuestRating(updates.getGuestRating());
    hotel.setReviewCount(updates.getReviewCount());
    hotel.setAddress(updates.getAddress());
    hotel.setAddressTh(updates.getAddressTh());
    hotel.setNeighborhood(updates.getNeighborhood());
    hotel.setNeighborhoodTh(updates.getNeighborhoodTh());
    hotel.setCheckInFrom(updates.getCheckInFrom());
    hotel.setCheckOutUntil(updates.getCheckOutUntil());
    hotel.setPhone(updates.getPhone());
    // manager + saasPlan only change via updateManagement
    Hotel updatedHotel = hotelRepository.save(hotel);
    publishEvent(HotelEventType.UPDATED, updatedHotel);
    indexUpsert(updatedHotel);
    return updatedHotel;
  }

  @Transactional
  @Caching(
      put = @CachePut(cacheNames = "hotelById", key = "#result.id"),
      evict = @CacheEvict(cacheNames = "hotelsByCity", allEntries = true))
  public Hotel updateManagement(Long id, UpdateHotelManagementRequest request) {
    User actor = hotelAccessService.requireCurrentUser();
    if (!hotelAccessService.isAdmin(actor)) {
      throw new ForbiddenException("Only ADMIN can update hotel management and SaaS plan");
    }
    Hotel hotel = findById(id);
    if (request.managerId() != null) {
      User manager =
          userRepository
              .findById(request.managerId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Manager not found: " + request.managerId()));
      if (manager.getRole() != Role.HOTEL_MANAGER && manager.getRole() != Role.ADMIN) {
        throw new ForbiddenException("Assigned user must have HOTEL_MANAGER or ADMIN role");
      }
      hotel.setManager(manager);
    } else {
      hotel.setManager(null);
    }
    hotel.setSaasPlan(request.saasPlan());
    Hotel updatedHotel = hotelRepository.save(hotel);
    publishEvent(HotelEventType.UPDATED, updatedHotel);
    return updatedHotel;
  }

  @Transactional
  @Caching(
      evict = {
        @CacheEvict(cacheNames = "hotelById", key = "#id"),
        @CacheEvict(cacheNames = "hotelsByCity", allEntries = true)
      })
  public void delete(Long id) {
    hotelAccessService.assertCanManageHotel(id);
    Hotel hotel = findById(id);
    hotelRepository.deleteById(id);
    publishEvent(HotelEventType.DELETED, hotel);
    indexDelete(id);
  }

  private void publishEvent(HotelEventType eventType, Hotel hotel) {
    if (hotelEventPublisher != null) {
      hotelEventPublisher.publish(eventType, hotel);
    }
  }

  private void indexUpsert(Hotel hotel) {
    HotelIndexSync hotelIndexSync = hotelIndexSyncProvider.getIfAvailable();
    if (hotelIndexSync != null) {
      hotelIndexSync.upsert(hotel);
    }
  }

  private void indexDelete(Long id) {
    HotelIndexSync hotelIndexSync = hotelIndexSyncProvider.getIfAvailable();
    if (hotelIndexSync != null) {
      hotelIndexSync.delete(id);
    }
  }
}
