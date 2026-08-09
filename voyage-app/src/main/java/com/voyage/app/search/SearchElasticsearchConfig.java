package com.voyage.app.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Activates Spring Data Elasticsearch repositories only when the search lab is enabled. Unit tests
 * set {@code application.search.enabled=false} and exclude ES autoconfig so CI stays Docker-free.
 */
@Configuration
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableElasticsearchRepositories(basePackages = "com.voyage.app.search")
public class SearchElasticsearchConfig {}
