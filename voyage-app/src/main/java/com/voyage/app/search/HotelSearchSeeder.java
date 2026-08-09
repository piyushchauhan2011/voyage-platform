package com.voyage.app.search;

import com.voyage.app.hotel.Hotel;
import com.voyage.app.hotel.HotelRepository;
import com.voyage.app.hotel.SaasPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a large bilingual hotel catalogue so Elasticsearch Thai/English search has something to
 * prove. Idempotent by English hotel name.
 */
@Service
@ConditionalOnProperty(
    name = "application.search.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HotelSearchSeeder {

  private static final int DEFAULT_COUNT = 100;
  private static final int MAX_COUNT = 200;

  private final HotelRepository hotelRepository;

  public HotelSearchSeeder(HotelRepository hotelRepository) {
    this.hotelRepository = hotelRepository;
  }

  @Transactional
  public SeedResult seed(Integer count) {
    int target = count == null ? DEFAULT_COUNT : Math.clamp(count, 1, MAX_COUNT);
    List<Hotel> catalog = catalog(target);
    List<String> existingNames =
        hotelRepository.findAll().stream().map(Hotel::getName).map(String::toLowerCase).toList();

    List<Hotel> toCreate =
        catalog.stream()
            .filter(hotel -> !existingNames.contains(hotel.getName().toLowerCase(Locale.ROOT)))
            .toList();

    List<Hotel> created = hotelRepository.saveAll(toCreate);
    return new SeedResult(
        created.size(),
        catalog.size() - created.size(),
        hotelRepository.count(),
        created.stream().limit(12).map(Hotel::getName).toList(),
        "Hotels are in Postgres only until you Reindex. Elasticsearch does not read SQL tables by itself.",
        "Try autocomplete on กรุ / Phu, then open Details on a result card.");
  }

  private List<Hotel> catalog(int count) {
    List<City> cities =
        List.of(
            new City(
                "Bangkok",
                "กรุงเทพ",
                "city hub",
                "ใจกลางเมือง",
                "Sukhumvit",
                "สุขุมวิท",
                "12 Sukhumvit Soi 11",
                "ซอยสุขุมวิท 11"),
            new City(
                "Phuket",
                "ภูเก็ต",
                "beach and sand",
                "ชายหาดและทะเล",
                "Patong",
                "ป่าตอง",
                "88 Beach Road",
                "ถนนชายหาด 88"),
            new City(
                "Chiang Mai",
                "เชียงใหม่",
                "mountain temples",
                "วัดบนภูเขา",
                "Old City",
                "เมืองเก่า",
                "45 Ratchadamnoen Rd",
                "ถนนราชดำเนิน 45"),
            new City(
                "Krabi",
                "กระบี่",
                "limestone cliffs by the sea",
                "หน้าผาหินปูนริมทะเล",
                "Ao Nang",
                "อ่าวนาง",
                "9 Cliffside Lane",
                "ซอยหน้าผา 9"),
            new City(
                "Hua Hin",
                "หัวหิน",
                "quiet beach promenade",
                "ทางเดินชายหาดเงียบสงบ",
                "Town Centre",
                "ใจกลางเมือง",
                "22 Phetkasem Rd",
                "ถนนเพชรเกษม 22"),
            new City(
                "Pattaya",
                "พัทยา",
                "nightlife near the shore",
                "ชีวิตกลางคืนใกล้ชายฝั่ง",
                "Central Pattaya",
                "พัทยากลาง",
                "101 Beachfront Ave",
                "ถนนริมหาด 101"),
            new City(
                "Ayutthaya",
                "อยุธยา",
                "historic ruins",
                "โบราณสถาน",
                "Historic Park",
                "อุทยานประวัติศาสตร์",
                "7 U-Thong Rd",
                "ถนนอู่ทอง 7"),
            new City(
                "Tokyo",
                "โตเกียว",
                "neon streets",
                "ถนนไฟนีออน",
                "Shinjuku",
                "ชินจูกุ",
                "3-1-1 Nishi-Shinjuku",
                "นิชิชินจูกุ 3-1-1"),
            new City(
                "Singapore",
                "สิงคโปร์",
                "harbour skyline",
                "เส้นขอบฟ้าท่าเรือ",
                "Marina Bay",
                "มารีน่าเบย์",
                "1 Bayfront Ave",
                "เบย์ฟรอนต์ 1"),
            new City(
                "Bali",
                "บาหลี",
                "rice terraces and surf",
                "นาขั้นบันไดและคลื่นเซิร์ฟ",
                "Canggu",
                "ชางกู",
                "18 Surf Street",
                "ถนนเซิร์ฟ 18"));

    List<Theme> themes =
        List.of(
            new Theme(
                "Saltbreeze Inn",
                "โรงแรมสายลมเค็ม",
                "Steps from the sand with surf audible at night.",
                "ใกล้มหาสมุทร เสียงคลื่นเซิร์ฟดังตอนค่ำ",
                "beach, wifi, breakfast"),
            new Theme(
                "Temple View Lodge",
                "ลอดจ์วิววัด",
                "Quiet rooms overlooking temples and morning mist.",
                "ห้องเงียบมองเห็นวัดและหมอกยามเช้า",
                "temple view, wifi, garden"),
            new Theme(
                "Harbour Lights Hotel",
                "โรงแรมไฟท่าเรือ",
                "Harbour-facing suites for late dinners and city walks.",
                "ห้องสวีทหันท่าเรือ สำหรับมื้อค่ำและเดินเล่นในเมือง",
                "harbour, restaurant, wifi"),
            new Theme(
                "Market Lane Guesthouse",
                "เกสต์เฮาส์ตรอกตลาด",
                "Budget stay beside the night market and street food.",
                "ที่พักราคาประหยัดข้างตลาดกลางคืนและอาหารข้างทาง",
                "market, wifi, shared kitchen"),
            new Theme(
                "Cliffside Resort",
                "รีสอร์ทริมหน้าผา",
                "Pool decks above limestone cliffs and turquoise water.",
                "ระเบียงสระเหนือหน้าผาหินปูนและน้ำสีฟ้าเขียว",
                "pool, sea view, spa"),
            new Theme(
                "Old Town Boutique",
                "บูติกเมืองเก่า",
                "Restored wooden house in the historic quarter.",
                "บ้านไม้บูรณะในย่านประวัติศาสตร์",
                "heritage, wifi, cafe"),
            new Theme(
                "Riverfront Suites",
                "สวีทริมแม่น้ำ",
                "Balconies over the river with longtail boats passing by.",
                "ระเบียงเหนือแม่น้ำ มีเรือหางยาวผ่านไปมา",
                "river view, breakfast, wifi"),
            new Theme(
                "Palm Grove Hotel",
                "โรงแรมดงปาล์ม",
                "Palm-shaded courtyard a short walk from the beach.",
                "ลานเงาปาล์ม เดินไม่ไกลจากชายหาด",
                "palm garden, beach nearby, wifi"),
            new Theme(
                "Skyline Capsule",
                "แคปซูลเส้นขอบฟ้า",
                "Compact pods with skyline views for digital nomads.",
                "แคปซูลกะทัดรัดพร้อมวิวเส้นขอบฟ้าสำหรับโนแมด",
                "capsule, wifi, coworking"),
            new Theme(
                "Garden Spa Retreat",
                "รีทรีทสวนสปา",
                "Slow mornings in a tropical garden spa retreat.",
                "เช้าวันสบายในรีทรีทสปาสวนเขตร้อน",
                "spa, garden, yoga"));

    List<Hotel> hotels = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      City city = cities.get(i % cities.size());
      Theme theme = themes.get(i % themes.size());
      int series = (i / themes.size()) + 1;
      String name = theme.enName() + " " + city.en() + " #" + series;
      String nameTh = theme.thName() + " " + city.th() + " #" + series;
      double price = 55.0 + (i % 17) * 12.5 + (i % 5) * 3.0;
      String description =
          theme.enDescription() + " Located in " + city.en() + ", known for " + city.enVibe() + ".";
      String descriptionTh =
          theme.thDescription()
              + " ตั้งอยู่ที่"
              + city.th()
              + " มีชื่อเรื่อง"
              + city.thVibe()
              + ".";
      Hotel hotel =
          new Hotel(
              name,
              city.en(),
              price,
              description,
              theme.amenities(),
              nameTh,
              city.th(),
              descriptionTh);
      hotel.setSaasPlan(i % 7 == 0 ? SaasPlan.PRO : SaasPlan.FREE);
      String seed = slug(name);
      hotel.setImageUrl("https://picsum.photos/seed/" + seed + "/800/500");
      hotel.setGalleryUrls(
          "https://picsum.photos/seed/"
              + seed
              + "-a/800/500,"
              + "https://picsum.photos/seed/"
              + seed
              + "-b/800/500,"
              + "https://picsum.photos/seed/"
              + seed
              + "-c/800/500");
      hotel.setStarRating(3 + (i % 3));
      hotel.setGuestRating(7.5 + (i % 20) * 0.1);
      hotel.setReviewCount(40 + (i * 17) % 900);
      hotel.setAddress(city.enAddress() + ", " + city.en());
      hotel.setAddressTh(city.thAddress() + " " + city.th());
      hotel.setNeighborhood(city.enNeighborhood());
      hotel.setNeighborhoodTh(city.thNeighborhood());
      hotel.setCheckInFrom("14:00");
      hotel.setCheckOutUntil("12:00");
      hotel.setPhone("+66-2-" + String.format("%03d-%04d", 100 + (i % 800), 1000 + (i % 8000)));
      hotels.add(hotel);
    }
    return hotels;
  }

  private static String slug(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  private record City(
      String en,
      String th,
      String enVibe,
      String thVibe,
      String enNeighborhood,
      String thNeighborhood,
      String enAddress,
      String thAddress) {}

  private record Theme(
      String enName, String thName, String enDescription, String thDescription, String amenities) {}

  public record SeedResult(
      int created,
      int skippedExisting,
      long totalHotels,
      List<String> sampleNames,
      String lesson,
      String tryNext) {}
}
