package com.voyage.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Demonstrates explicit @Bean registration.
 *
 * Spring scans @Configuration classes at startup, calls each @Bean method once,
 * and stores the returned object in the ApplicationContext. Any other bean that
 * declares Clock as a dependency will receive the same singleton instance.
 *
 * Compare this to @Component-based registration:
 *   @Configuration + @Bean  → you control construction (useful when wiring third-party objects)
 *   @Component              → Spring constructs the object for you
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
