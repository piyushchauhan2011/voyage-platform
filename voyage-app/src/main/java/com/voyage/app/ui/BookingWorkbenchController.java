package com.voyage.app.ui;

import com.voyage.app.hotel.HotelService;
import com.voyage.app.inventory.RoomType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui/bookings")
public class BookingWorkbenchController {

    private final HotelService hotelService;

    public BookingWorkbenchController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping
    public String workbench(Model model) {
        model.addAttribute("hotels", hotelService.findAll());
        model.addAttribute("roomTypes", RoomType.values());
        return "booking-workbench";
    }
}