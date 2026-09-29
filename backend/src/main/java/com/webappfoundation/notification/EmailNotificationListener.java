package com.webappfoundation.notification;

import com.webappfoundation.event.SupportCustomerMessageEvent;
import com.webappfoundation.event.SupportRepliedEvent;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends transactional email in reaction to FOUNDATION domain events, off the request thread
 * ({@code @Async}) and only after the triggering transaction commits ({@code AFTER_COMMIT};
 * fallbackExecution=true so it also runs when there is no surrounding transaction). Depends only on
 * {@link EmailService} + {@link UserRepository}, so the direction is always domain → notification (no
 * bean cycle). Generated projects add their own listeners here for their own events.
 */
@Component
public class EmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    private final EmailService emailService;
    private final UserRepository userRepository;

    /** Where staff notifications go — the seeded admin's address. */
    @Value("${admin.email:}")
    private String adminEmail;

    public EmailNotificationListener(EmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    // ── Support reply → the customer who owns the case ───────────────────────────
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSupportReplied(SupportRepliedEvent event) {
        if (event.getUserId() == null) {
            return;
        }
        String to = userRepository.findById(event.getUserId()).map(User::getEmail).orElse(null);
        if (to == null || to.isBlank()) {
            log.warn("Support case #{} has no customer email — skipping reply notification", event.getCaseId());
            return;
        }
        emailService.sendHtml(to,
                "Update on your support request: " + event.getSubject(),
                supportReplyHtml(event));
    }

    // ── Customer message (new case or reply) → the admin ─────────────────────────
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSupportCustomerMessage(SupportCustomerMessageEvent event) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("No admin.email configured — skipping support alert for case #{}", event.getCaseId());
            return;
        }
        User customer = event.getUserId() == null ? null
                : userRepository.findById(event.getUserId()).orElse(null);
        String name = customer == null ? "A customer"
                : ((customer.getFirstName() == null ? "" : customer.getFirstName()) + " "
                        + (customer.getLastName() == null ? "" : customer.getLastName())).trim();
        String customerEmail = customer == null ? null : customer.getEmail();
        String subject = (event.isNewCase() ? "New support request" : "New reply")
                + " #" + event.getCaseId() + ": " + safe(event.getSubject());
        emailService.sendHtml(adminEmail, subject, customerMessageHtml(event, name, customerEmail));
    }

    private String customerMessageHtml(SupportCustomerMessageEvent e, String name, String customerEmail) {
        String from = safe(name) + (customerEmail == null || customerEmail.isBlank() ? "" : " (" + customerEmail + ")");
        return "<div style='font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#212121'>"
                + "<h2 style='color:#1A3A6D'>" + (e.isNewCase() ? "New support request" : "New customer reply") + "</h2>"
                + "<p><strong>Case:</strong> #" + e.getCaseId() + " — " + safe(e.getSubject()) + "<br>"
                + "<strong>From:</strong> " + from + "</p>"
                + "<p><strong>Message:</strong><br>" + safe(e.getMessageBody()) + "</p>"
                + "<hr style='border:none;border-top:1px solid #eee'>"
                + "<p style='color:#888;font-size:12px'>Reply from the admin helpdesk at /admin/support.</p>"
                + "</div>";
    }

    private String supportReplyHtml(SupportRepliedEvent e) {
        String reply = (e.getReplyBody() == null || e.getReplyBody().isBlank())
                ? "" : "<p><strong>Our reply:</strong><br>" + e.getReplyBody() + "</p>";
        return "<div style='font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#212121'>"
                + "<h2 style='color:#1A3A6D'>Support request update</h2>"
                + "<p>Your request <strong>#" + e.getCaseId() + " — " + safe(e.getSubject()) + "</strong> is now <strong>"
                + e.getStatus() + "</strong>.</p>"
                + reply
                + "<hr style='border:none;border-top:1px solid #eee'>"
                + "<p style='color:#888;font-size:12px'>You're receiving this because you opened a support request with us.</p>"
                + "</div>";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
