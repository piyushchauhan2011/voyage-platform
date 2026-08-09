package com.voyage.app.config;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * Cookie-based locale switching for Thymeleaf pages. Append {@code ?lang=th} or {@code ?lang=en} to
 * change UI language; the choice sticks via the {@code VOYAGE_LOCALE} cookie.
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

  public static final String LOCALE_COOKIE = "VOYAGE_LOCALE";

  @Bean
  LocaleResolver localeResolver() {
    CookieLocaleResolver resolver = new CookieLocaleResolver(LOCALE_COOKIE);
    resolver.setDefaultLocale(Locale.ENGLISH);
    resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
    return resolver;
  }

  @Bean
  LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName("lang");
    return interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(localeChangeInterceptor());
  }
}
