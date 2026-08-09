package com.voyage.app.search;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Elasticsearch projection of a hotel. Postgres remains the source of truth; this document exists
 * so full-text search can use language-specific analyzers.
 *
 * <p>English fields use the built-in {@code english} analyzer (stemming + stopwords). Thai fields
 * use the built-in {@code thai} analyzer, which segments text without relying on spaces.
 */
@Document(
    indexName = "#{@environment.getProperty('application.search.index', 'hotels')}",
    createIndex = false)
@Getter
@Setter
@NoArgsConstructor
public class HotelDocument {

  @Id private Long id;

  @Field(type = FieldType.Text, analyzer = "english", searchAnalyzer = "english")
  private String name;

  @Field(type = FieldType.Text, analyzer = "thai", searchAnalyzer = "thai")
  private String nameTh;

  @Field(type = FieldType.Text, analyzer = "english", searchAnalyzer = "english")
  private String city;

  @Field(type = FieldType.Text, analyzer = "thai", searchAnalyzer = "thai")
  private String cityTh;

  @Field(type = FieldType.Text, analyzer = "english", searchAnalyzer = "english")
  private String description;

  @Field(type = FieldType.Text, analyzer = "thai", searchAnalyzer = "thai")
  private String descriptionTh;

  @Field(type = FieldType.Text, analyzer = "english", searchAnalyzer = "english")
  private String amenities;

  @Field(type = FieldType.Double)
  private Double pricePerNight;

  public static HotelDocument from(com.voyage.app.hotel.Hotel hotel) {
    HotelDocument document = new HotelDocument();
    document.setId(hotel.getId());
    document.setName(hotel.getName());
    document.setNameTh(hotel.getNameTh());
    document.setCity(hotel.getCity());
    document.setCityTh(hotel.getCityTh());
    document.setDescription(hotel.getDescription());
    document.setDescriptionTh(hotel.getDescriptionTh());
    document.setAmenities(hotel.getAmenities());
    document.setPricePerNight(hotel.getPricePerNight());
    return document;
  }
}
