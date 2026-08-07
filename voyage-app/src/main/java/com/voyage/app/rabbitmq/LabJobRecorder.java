package com.voyage.app.rabbitmq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
@ConditionalOnProperty(name = "application.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class LabJobRecorder {

    private static final int MAX_DELIVERIES = 50;

    private final Deque<LabJobDelivery> recentDeliveries = new ConcurrentLinkedDeque<>();

    public void record(LabJobDelivery delivery) {
        recentDeliveries.addFirst(delivery);
        while (recentDeliveries.size() > MAX_DELIVERIES) {
            recentDeliveries.removeLast();
        }
    }

    public List<LabJobDelivery> recentDeliveries() {
        return new ArrayList<>(recentDeliveries);
    }

    public void clear() {
        recentDeliveries.clear();
    }
}
