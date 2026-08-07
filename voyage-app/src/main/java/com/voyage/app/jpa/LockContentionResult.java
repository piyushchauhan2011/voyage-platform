package com.voyage.app.jpa;

public record LockContentionResult(
    Long inventoryId,
    String stayDate,
    String holder,
    String contender,
    long elapsedMs,
    int roomsAfter,
    String tip) {}
