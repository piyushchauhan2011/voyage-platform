package com.voyage.app.search;

import com.voyage.app.hotel.Hotel;

/** Keeps the Elasticsearch hotel index in sync with Postgres writes when search is enabled. */
public interface HotelIndexSync {

  void upsert(Hotel hotel);

  void delete(Long hotelId);
}
