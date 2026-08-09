package com.voyage.app.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface HotelSearchRepository extends ElasticsearchRepository<HotelDocument, Long> {}
