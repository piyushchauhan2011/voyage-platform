package com.voyage.app.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the Spring bean lifecycle:
 *
 * <p>1. Spring instantiates the class (calls constructor) 2. Spring injects dependencies
 * 3. @PostConstruct — safe to use injected dependencies here 4. Bean is ready and serves requests
 * 5. @PreDestroy — called on application shutdown (release resources, close connections)
 *
 * <p>Run the app and watch the logs to see steps 1→3 on startup and step 5 on shutdown (Ctrl+C).
 */
@Component
public class BeanLifecycleDemo {

  private static final Logger log = LoggerFactory.getLogger(BeanLifecycleDemo.class);

  public BeanLifecycleDemo() {
    log.info("[Lifecycle 1] BeanLifecycleDemo instantiated");
  }

  @PostConstruct
  public void init() {
    log.info("[Lifecycle 2] @PostConstruct — all dependencies are injected");
  }

  @PreDestroy
  public void destroy() {
    log.info("[Lifecycle 3] @PreDestroy — shutting down, release resources here");
  }
}
