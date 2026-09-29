package com.webappfoundation.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over the auto-configured {@link JavaMailSender} (driven by spring.mail.* / SMTP_* env).
 * Sends HTML email and never throws — a mail failure (e.g. SMTP down) is logged, not propagated, so it
 * can never break the business flow that triggered it (support reply, order confirmation, OTP, …).
 * Inject it anywhere you need to send transactional email.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.from:No Reply <no-reply@example.local>}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Send an HTML email. Returns true on success; logs and returns false on any failure. */
    public boolean sendHtml(String to, String subject, String html) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email '{}' — no recipient address", subject);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent email '{}' to {}", subject, to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
            return false;
        }
    }
}
