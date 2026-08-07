package com.voyage.app.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Spring Framework 6 disabled trailing-slash matching. Browsers that hit {@code /ui/kafka/} would
 * otherwise miss {@code @GetMapping} on {@code /ui/kafka} and fall through to an error path. This
 * filter trims the slash before MVC mapping.
 */
@Configuration
public class WebMvcConfig {

  @Bean
  FilterRegistrationBean<UrlHandlerFilter> trailingSlashUrlHandlerFilter() {
    UrlHandlerFilter filter = UrlHandlerFilter.trailingSlashHandler("/ui/**").wrapRequest().build();
    FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
