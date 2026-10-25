package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;

public interface NotificationService {

    DeliveryResult send(Delivery delivery);

    boolean isAvailable();

    String getChannel();
}