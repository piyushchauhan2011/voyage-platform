package com.voyage.app.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA generates the SQL implementation at runtime — no boilerplate needed.
 *
 * JpaRepository<Hotel, Long> gives you: findAll, findById, save, deleteById, count, etc.
 * Spring derives the query for findByCity from the method name: WHERE city = ?
 */
@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

    List<Hotel> findByCity(String city);
}
