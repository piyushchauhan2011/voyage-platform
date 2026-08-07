package com.voyage.app.hotel;

import com.voyage.app.exception.ResourceNotFoundException;

public class HotelNotFoundException extends ResourceNotFoundException {

  public HotelNotFoundException(Long id) {
    super("Hotel not found: " + id);
  }
}
