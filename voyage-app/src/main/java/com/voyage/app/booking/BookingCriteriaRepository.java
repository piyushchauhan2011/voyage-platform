package com.voyage.app.booking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class BookingCriteriaRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Booking> search(BookingSearchCriteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Booking> query = builder.createQuery(Booking.class);
        Root<Booking> root = query.from(Booking.class);

        List<Predicate> predicates = new ArrayList<>();
        if (criteria.userId() != null) {
            predicates.add(builder.equal(root.get("user").get("id"), criteria.userId()));
        }
        if (criteria.hotelId() != null) {
            predicates.add(builder.equal(root.get("hotel").get("id"), criteria.hotelId()));
        }
        if (criteria.status() != null) {
            predicates.add(builder.equal(root.get("status"), criteria.status()));
        }
        if (criteria.checkInFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("checkIn"), criteria.checkInFrom()));
        }
        if (criteria.checkInTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("checkIn"), criteria.checkInTo()));
        }

        query.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.asc(root.get("checkIn")));

        TypedQuery<Booking> typedQuery = entityManager.createQuery(query);
        return typedQuery.getResultList();
    }
}