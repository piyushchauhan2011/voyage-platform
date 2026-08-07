package com.voyage.app.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui/jpa")
public class JpaLabController {

    @GetMapping
    public String lab() {
        return "jpa-lab";
    }
}
