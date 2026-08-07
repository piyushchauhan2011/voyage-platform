package com.voyage.app.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KafkaDashboardControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void dashboardRendersWithoutTrailingSlash() throws Exception {
    mockMvc
        .perform(get("/ui/kafka"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Kafka hotel events")));
  }

  @Test
  void dashboardRendersWithTrailingSlash() throws Exception {
    // Spring 6 no longer matches trailing slashes by default; UrlHandlerFilter wraps the request.
    mockMvc
        .perform(get("/ui/kafka/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Kafka hotel events")));
  }
}
