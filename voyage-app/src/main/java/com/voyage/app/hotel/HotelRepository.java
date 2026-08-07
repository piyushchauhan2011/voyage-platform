package com.voyage.app.hotel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA generates the SQL implementation at runtime — no boilerplate needed.
 *
 * <p>JpaRepository&lt;Hotel, Long&gt; gives you: findAll, findById, save, deleteById, count, etc.
 * Spring derives the query for findByCity from the method name: WHERE city = ?
 */
@Repository
public interface HotelRepository
    extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

  List<Hotel> findByCity(String city);

  List<Hotel> findByManager_Id(Long managerId);

  long countByManager_Id(Long managerId);
}
