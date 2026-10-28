package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${notification.email.from:alerts@tracking-system.com}")
    private String fromEmail;

    @Value("${notification.email.enabled:false}")
    private boolean enabled;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public DeliveryResult send(Delivery delivery) {
        if (!enabled) {
            logger.warn("Email notifications are disabled");
            return DeliveryResult.failure("Email notifications are disabled", ErrorType.PERMANENT);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(delivery.getRecipient());
            helper.setSubject(delivery.getTitle());
            helper.setText(delivery.getMessage(), false);

            mailSender.send(message);

            delivery.markSent();
            logger.info("Email notification sent to: {}", delivery.getRecipient());
            return DeliveryResult.success();

        } catch (Exception e) {
            logger.error("Failed to send email notification", e);
            return classifyError(e);
        }
    }

    private DeliveryResult classifyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("address") || msg.contains("invalid email") || msg.contains("mailbox")) {
            return DeliveryResult.failure(e.getMessage(), ErrorType.PERMANENT);
        }
        return DeliveryResult.failure(e.getMessage(), ErrorType.TRANSIENT);
    }

    @Override
    public boolean isAvailable() {
        return enabled && mailSender != null;
    }

    @Override
    public String getChannel() {
        return "EMAIL";
    }
}