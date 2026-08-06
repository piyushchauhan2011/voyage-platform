package com.voyage.app.ui;

import com.voyage.app.hotel.HotelService;
import com.voyage.app.kafka.DeadLetterHotelEvent;
import com.voyage.app.kafka.DeadLetterHotelEventService;
import com.voyage.app.kafka.HotelEventStatusService;
import com.voyage.app.kafka.ProcessedHotelEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/ui/kafka")
public class KafkaDashboardController {

    private final HotelService hotelService;
    private final HotelEventStatusService hotelEventStatusService;
    private final DeadLetterHotelEventService deadLetterHotelEventService;
    private final String hotelEventsTopic;
    private final String hotelEventsDeadLetterTopic;
    private final String consumerGroupId;

    public KafkaDashboardController(HotelService hotelService,
                                    HotelEventStatusService hotelEventStatusService,
                                    DeadLetterHotelEventService deadLetterHotelEventService,
                                    @Value("${application.kafka.topic.hotel-events}") String hotelEventsTopic,
                                    @Value("${application.kafka.topic.hotel-events-dlt}") String hotelEventsDeadLetterTopic,
                                    @Value("${spring.kafka.consumer.group-id}") String consumerGroupId) {
        this.hotelService = hotelService;
        this.hotelEventStatusService = hotelEventStatusService;
        this.deadLetterHotelEventService = deadLetterHotelEventService;
        this.hotelEventsTopic = hotelEventsTopic;
        this.hotelEventsDeadLetterTopic = hotelEventsDeadLetterTopic;
        this.consumerGroupId = consumerGroupId;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("topicName", hotelEventsTopic);
        model.addAttribute("deadLetterTopicName", hotelEventsDeadLetterTopic);
        model.addAttribute("consumerGroupId", consumerGroupId);
        model.addAttribute("hotels", hotelService.findAll());
        model.addAttribute("recentEvents", toView(hotelEventStatusService.getRecentEvents()));
        model.addAttribute("recentDeadLetters", toDeadLetterView(deadLetterHotelEventService.getRecentDeadLetters()));
        return "kafka-dashboard";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("topicName", hotelEventsTopic);
        model.addAttribute("deadLetterTopicName", hotelEventsDeadLetterTopic);
        model.addAttribute("consumerGroupId", consumerGroupId);
        model.addAttribute("eventHistory", toView(hotelEventStatusService.getEventHistory()));
        model.addAttribute("deadLetterHistory", toDeadLetterView(deadLetterHotelEventService.getDeadLetterHistory()));
        return "kafka-history";
    }

    @GetMapping("/status")
    @ResponseBody
    public List<KafkaEventStatusView> status() {
        return toView(hotelEventStatusService.getRecentEvents());
    }

    @GetMapping("/dead-letters")
    @ResponseBody
    public List<DeadLetterEventView> deadLetters() {
        return toDeadLetterView(deadLetterHotelEventService.getRecentDeadLetters());
    }

    private List<KafkaEventStatusView> toView(List<ProcessedHotelEvent> events) {
        return events.stream()
                .map(event -> new KafkaEventStatusView(
                        event.getEventId(),
                        event.getSchemaVersion(),
                        event.getEventType().name(),
                        event.getHotelId(),
                        event.getHotelName(),
                        event.getCity(),
                        event.getPricePerNight(),
                        event.getTopicName(),
                        event.getMessageKey(),
                        event.getPartitionId(),
                        event.getKafkaOffset(),
                        event.getConsumerGroupId(),
                        event.getOccurredAt(),
                        event.getProcessedAt()
                ))
                .toList();
    }

    private List<DeadLetterEventView> toDeadLetterView(List<DeadLetterHotelEvent> events) {
        return events.stream()
                .map(event -> new DeadLetterEventView(
                        event.getId(),
                        event.getOriginalTopic(),
                        event.getDeadLetterTopic(),
                        event.getMessageKey(),
                        event.getPartitionId(),
                        event.getKafkaOffset(),
                        event.getPayload(),
                        event.getErrorClassName(),
                        event.getErrorMessage(),
                        event.getDeadLetteredAt()
                ))
                .toList();
    }
}