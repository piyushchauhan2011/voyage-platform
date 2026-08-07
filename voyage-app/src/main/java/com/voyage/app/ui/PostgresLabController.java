package com.voyage.app.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui/postgres")
public class PostgresLabController {

  @GetMapping
  public String lab() {
    return "postgres-lab";
  }
}
