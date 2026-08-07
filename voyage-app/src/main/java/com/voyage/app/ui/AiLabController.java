package com.voyage.app.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui/ai")
public class AiLabController {

  @GetMapping
  public String lab() {
    return "ai-lab";
  }
}
