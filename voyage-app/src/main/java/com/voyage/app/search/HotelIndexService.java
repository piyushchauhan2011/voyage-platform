package com.voyage.app.search;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

/**
 * Creates the hotels index with language-aware mappings and bulk-loads documents from Postgres.
 *
 * <p>Teaching point: deleting + recreating the index is fine for a lab; production uses aliases and
 * zero-downtime reindex instead.
 */
@Service
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HotelIndexService implements HotelIndexSync {

  private static final Logger log = LoggerFactory.getLogger(HotelIndexService.class);

  private final ElasticsearchOperations elasticsearchOperations;
  private final HotelSearchRepository hotelSearchRepository;
  private final HotelRepository hotelRepository;
  private final String indexName;

  public HotelIndexService(
      ElasticsearchOperations elasticsearchOperations,
      HotelSearchRepository hotelSearchRepository,
      HotelRepository hotelRepository,
      @Value("${application.search.index:hotels}") String indexName) {
    this.elasticsearchOperations = elasticsearchOperations;
    this.hotelSearchRepository = hotelSearchRepository;
    this.hotelRepository = hotelRepository;
    this.indexName = indexName;
  }

  @Override
  public void upsert(Hotel hotel) {
    hotelSearchRepository.save(HotelDocument.from(hotel));
  }

  @Override
  public void delete(Long hotelId) {
    hotelSearchRepository.deleteById(hotelId);
  }

  public ReindexResult reindex() {
    ensureIndex();
    hotelSearchRepository.deleteAll();
    List<Hotel> hotels = hotelRepository.findAll();
    List<HotelDocument> documents = hotels.stream().map(HotelDocument::from).toList();
    if (!documents.isEmpty()) {
      hotelSearchRepository.saveAll(documents);
    }
    long indexed = hotelSearchRepository.count();
    log.info("Reindexed {} hotels into Elasticsearch index '{}'", indexed, indexName);
    return new ReindexResult(
        indexName,
        hotels.size(),
        indexed,
        "Postgres is the source of truth. Elasticsearch is a derived full-text index — reindex "
            + "after seeding or whenever the mapping changes.");
  }

  public void ensureIndex() {
    IndexCoordinates coordinates = IndexCoordinates.of(indexName);
    IndexOperations indexOps = elasticsearchOperations.indexOps(coordinates);
    if (!indexOps.exists()) {
      indexOps.create();
      indexOps.putMapping(HotelDocument.class);
      log.info("Created Elasticsearch index '{}'", indexName);
    }
  }

  public StatusResult status() {
    boolean reachable = false;
    long docCount = 0;
    boolean indexExists = false;
    String detail = "Elasticsearch unreachable";
    try {
      IndexCoordinates coordinates = IndexCoordinates.of(indexName);
      IndexOperations indexOps = elasticsearchOperations.indexOps(coordinates);
      indexExists = indexOps.exists();
      reachable = true;
      if (indexExists) {
        docCount = hotelSearchRepository.count();
      }
      detail =
          indexExists
              ? "Index ready — search against English and Thai fields."
              : "Index missing — run Reindex after seeding.";
    } catch (Exception ex) {
      detail = "Elasticsearch error: " + ex.getMessage();
      log.warn("Elasticsearch status check failed: {}", ex.getMessage());
    }
    return new StatusResult(
        reachable, indexExists, indexName, docCount, hotelRepository.count(), detail);
  }

  public Map<String, Object> explain(String query, String lang) {
    return Map.of(
        "query",
        query == null ? "" : query,
        "lang",
        lang == null ? "en" : lang,
        "index",
        indexName,
        "strategy",
        "multi_match across EN + TH fields with locale-biased boosts",
        "englishFields",
        List.of("name^3", "city^2", "description^2", "amenities"),
        "thaiFields",
        List.of("nameTh^3", "cityTh^2", "descriptionTh^2"),
        "analyzers",
        Map.of(
            "english",
            "Built-in english analyzer — stemming and stopwords for space-separated text.",
            "thai",
            "Built-in thai analyzer — segments Thai script without spaces between words."),
        "tip",
        "Try q=beach then q=ชายหาด after seeding bilingual hotels. Same intent, different tokens.");
  }

  public record ReindexResult(String index, int postgresCount, long indexedCount, String lesson) {}

  public record StatusResult(
      boolean elasticsearchReachable,
      boolean indexExists,
      String index,
      long elasticsearchDocCount,
      long postgresHotelCount,
      String detail) {}
}
