package com.voyage.app.ui;

import com.voyage.app.jobs.JobsPlaygroundService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/ui/jobs")
@ConditionalOnProperty(
    name = "application.jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JobsLabController {

  private final JobsPlaygroundService jobsPlaygroundService;

  public JobsLabController(ObjectProvider<JobsPlaygroundService> jobsPlaygroundService) {
    this.jobsPlaygroundService = jobsPlaygroundService.getIfAvailable();
  }

  @GetMapping
  public String lab(Model model) {
    populate(model);
    return "jobs-lab";
  }

  @GetMapping("/fragments/delayed")
  public String delayedFragment(Model model) {
    populateDelayed(model);
    return "jobs/delayed :: delayed";
  }

  @GetMapping("/fragments/runs")
  public String runsFragment(Model model) {
    populateRuns(model);
    return "jobs/runs :: runs";
  }

  @GetMapping("/fragments/scheduler")
  public String schedulerFragment(Model model) {
    populateScheduler(model);
    return "jobs/scheduler :: scheduler";
  }

  @PostMapping("/actions/enqueue-immediate")
  public String enqueueImmediate(
      @RequestParam(required = false) String routingKey,
      @RequestParam(required = false) String payload,
      Model model) {
    requireService();
    try {
      jobsPlaygroundService.enqueueImmediate(routingKey, payload);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
    populateRuns(model);
    populateScheduler(model);
    return "jobs/runs :: runs";
  }

  @PostMapping("/actions/enqueue-delayed")
  public String enqueueDelayed(
      @RequestParam(required = false) String routingKey,
      @RequestParam(required = false) String payload,
      @RequestParam(required = false, defaultValue = "5") long delaySeconds,
      Model model) {
    requireService();
    try {
      jobsPlaygroundService.enqueueDelayed(routingKey, payload, delaySeconds);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
    populateDelayed(model);
    populateScheduler(model);
    return "jobs/delayed :: delayed";
  }

  @PostMapping("/actions/purge-delayed")
  public String purgeDelayed(Model model) {
    requireService();
    jobsPlaygroundService.purgeDelayed();
    populateDelayed(model);
    populateScheduler(model);
    return "jobs/delayed :: delayed";
  }

  private void populate(Model model) {
    populateDelayed(model);
    populateRuns(model);
    populateScheduler(model);
  }

  private void populateDelayed(Model model) {
    if (jobsPlaygroundService == null) {
      model.addAttribute("jobsDisabled", true);
      model.addAttribute("delayedJobs", java.util.List.of());
      return;
    }
    model.addAttribute("jobsDisabled", false);
    model.addAttribute("delayedJobs", jobsPlaygroundService.delayedJobs());
  }

  private void populateRuns(Model model) {
    if (jobsPlaygroundService == null) {
      model.addAttribute("jobsDisabled", true);
      model.addAttribute("jobRuns", java.util.List.of());
      return;
    }
    model.addAttribute("jobsDisabled", false);
    model.addAttribute("jobRuns", jobsPlaygroundService.recentRuns());
  }

  private void populateScheduler(Model model) {
    if (jobsPlaygroundService == null) {
      model.addAttribute("jobsDisabled", true);
      model.addAttribute("scheduler", null);
      return;
    }
    model.addAttribute("jobsDisabled", false);
    model.addAttribute("scheduler", jobsPlaygroundService.schedulerPanel());
  }

  private void requireService() {
    if (jobsPlaygroundService == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Jobs lab is disabled");
    }
  }
}
