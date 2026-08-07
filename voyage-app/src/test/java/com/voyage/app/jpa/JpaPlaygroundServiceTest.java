package com.voyage.app.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class JpaPlaygroundServiceTest {

    @Autowired
    JpaPlaygroundService jpaPlaygroundService;

    @Test
    void lifecycle_persistThenDetachDoesNotWriteMutation() {
        LifecyclePersistResult persisted = jpaPlaygroundService.persistHotel();
        assertNotNull(persisted.hotelId());
        assertTrue(persisted.afterPersist().contains("MANAGED"));

        LifecycleDetachResult detached = jpaPlaygroundService.detachAndMutate();
        assertFalse(detached.mutationPersisted());
        assertEquals(detached.originalName(), detached.nameAfterReload());
    }

    @Test
    void loading_nPlusOneUsesMoreQueriesThanEntityGraph() {
        jpaPlaygroundService.seed();

        LoadingDemoResult nPlusOne = jpaPlaygroundService.nPlusOne();
        LoadingDemoResult entityGraph = jpaPlaygroundService.entityGraph();

        assertTrue(nPlusOne.bookingCount() >= 1);
        assertTrue(entityGraph.bookingCount() >= 1);
        assertTrue(
                nPlusOne.queryCount() > entityGraph.queryCount(),
                () -> "expected N+1 queryCount (%d) > entity-graph (%d)"
                        .formatted(nPlusOne.queryCount(), entityGraph.queryCount())
        );
    }

    @Test
    void bookingRollback_restoresInventoryAndCreatesNoBooking() {
        jpaPlaygroundService.seed();

        BookingTxResult rollback = jpaPlaygroundService.bookingRollback();

        assertFalse(rollback.success());
        assertNull(rollback.bookingId());
        assertNotNull(rollback.error());
        assertEquals(rollback.bookingsBefore(), rollback.bookingsAfter());
        assertEquals(rollback.paymentsBefore(), rollback.paymentsAfter());
        assertEquals(rollback.roomsBefore(), rollback.roomsAfter());
    }

    @Test
    void bookingSuccess_commitsReservationAndPayment() {
        jpaPlaygroundService.seed();

        BookingTxResult success = jpaPlaygroundService.bookingSuccess();

        assertTrue(success.success());
        assertNotNull(success.bookingId());
        assertEquals("CONFIRMED", success.status());
        assertEquals(success.bookingsBefore() + 1, success.bookingsAfter());
        assertEquals(success.paymentsBefore() + 1, success.paymentsAfter());
        assertEquals(success.roomsBefore() - 1, success.roomsAfter());
    }

    @Test
    void propagationMap_documentsRequiredRequiresNewAndNotSupported() throws Exception {
        PropagationMapResult map = jpaPlaygroundService.propagationMap();
        assertTrue(map.methods().get("PaymentService.charge").contains("REQUIRED"));
        assertTrue(map.methods().get("NotificationWriter.writeBookingConfirmed").contains("REQUIRES_NEW"));
        assertTrue(map.methods().get("NotificationService.onBookingConfirmed").contains("NOT_SUPPORTED"));
        assertTrue(map.methods().get("BookingService.createBooking").contains("REPEATABLE_READ"));
    }

    @Test
    void queryApis_returnSamples() {
        jpaPlaygroundService.seed();

        QueryDemoResult jpql = jpaPlaygroundService.queryJpql();
        QueryDemoResult criteria = jpaPlaygroundService.queryCriteria();
        QueryDemoResult spec = jpaPlaygroundService.querySpec();

        assertTrue(jpql.resultCount() >= 1);
        assertTrue(criteria.resultCount() >= 1);
        assertTrue(spec.resultCount() >= 1);
        assertFalse(jpql.sample().isEmpty());
    }
}
