package com.voyage.app.jobs;

import com.voyage.app.rabbitmq.LabJobMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs/playground")
@ConditionalOnProperty(
    name = "application.jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JobsPlaygroundController {

  private final JobsPlaygroundService jobsPlaygroundService;

  public JobsPlaygroundController(JobsPlaygroundService jobsPlaygroundService) {
    this.jobsPlaygroundService = jobsPlaygroundService;
  }

  @PostMapping("/enqueue-immediate")
  public LabJobMessage enqueueImmediate(
      @RequestBody(required = false) JobsPlaygroundService.EnqueueImmediateRequest request) {
    try {
      JobsPlaygroundService.EnqueueImmediateRequest body =
          request == null ? new JobsPlaygroundService.EnqueueImmediateRequest(null, null) : request;
      return jobsPlaygroundService.enqueueImmediate(body.routingKey(), body.payload());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @PostMapping("/enqueue-delayed")
  public DelayedJob enqueueDelayed(
      @RequestBody(required = false) JobsPlaygroundService.EnqueueDelayedRequest request) {
    try {
      JobsPlaygroundService.EnqueueDelayedRequest body =
          request == null
              ? new JobsPlaygroundService.EnqueueDelayedRequest(null, null, 5L)
              : request;
      long delay = body.delaySeconds() == null ? 5L : body.delaySeconds();
      return jobsPlaygroundService.enqueueDelayed(body.routingKey(), body.payload(), delay);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @DeleteMapping("/delayed")
  public PurgeResponse purgeDelayed() {
    return new PurgeResponse(jobsPlaygroundService.purgeDelayed());
  }

  @GetMapping("/delayed")
  public java.util.List<DelayedJob> delayed() {
    return jobsPlaygroundService.delayedJobs();
  }

  @GetMapping("/runs")
  public java.util.List<JobRun> runs() {
    return jobsPlaygroundService.recentRuns();
  }

  @GetMapping("/scheduler")
  public JobsPlaygroundService.SchedulerPanelView scheduler() {
    return jobsPlaygroundService.schedulerPanel();
  }

  public record PurgeResponse(long purged) {}
}
