package com.ecommerce.notificationservice.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends notifications as real emails via SMTP (JavaMailSender).
 * Enabled by setting `notification.channel=email`, together with the
 * standard `spring.mail.*` properties for the SMTP server.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.channel", havingValue = "email")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Value("${notification.email.from:no-reply@ecommerce.local}")
    private String fromAddress;

    @Override
    public void send(String recipient, String subject, String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromAddress);
            mailMessage.setTo(recipient);
            mailMessage.setSubject(subject);
            mailMessage.setText(message);
            mailSender.send(mailMessage);
            log.info("Email notification sent to {}", recipient);
        } catch (Exception e) {
            throw new NotificationDeliveryException("Failed to send email to " + recipient, e);
        }
    }
}
