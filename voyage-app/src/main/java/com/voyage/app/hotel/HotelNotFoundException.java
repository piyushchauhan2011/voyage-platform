package com.voyage.app.hotel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Maps to 404 Not Found — Spring MVC reads @ResponseStatus when this exception propagates out of a controller
@ResponseStatus(HttpStatus.NOT_FOUND)
public class HotelNotFoundException extends RuntimeException {

    public HotelNotFoundException(Long id) {
        super("Hotel not found: " + id);
    }
}
