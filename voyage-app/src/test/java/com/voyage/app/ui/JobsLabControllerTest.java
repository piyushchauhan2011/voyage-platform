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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {"application.jobs.enabled=true", "application.jobs.async-notifications=false"})
class JobsLabControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void labRendersWithoutTrailingSlash() throws Exception {
    mockMvc
        .perform(get("/ui/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Scheduler, queues, and delayed jobs")));
  }

  @Test
  void labRendersWithTrailingSlash() throws Exception {
    mockMvc
        .perform(get("/ui/jobs/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Scheduler, queues, and delayed jobs")));
  }
}
