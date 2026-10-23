package com.webappfoundation.connector.builtin;

import com.webappfoundation.connector.ActionConnector;
import com.webappfoundation.connector.ConnectorAction;
import com.webappfoundation.connector.ConnectorCategory;
import com.webappfoundation.connector.ConnectorResult;
import com.webappfoundation.connector.OrderStageContext;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.service.ConnectorConfigService;
import com.webappfoundation.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Exposes the foundation email spine as a connector ({@code id="email"}) so "email customer" / "email store"
 * are bindable like any connector action — one uniform model. {@code email.customer} mails the order's owner;
 * {@code email.store} mails the configured admin address. No secrets are read or logged here.
 */
@Component
public class EmailConnector implements ActionConnector {

    public static final String ID = "email";
    public static final String EMAIL_CUSTOMER = "email.customer";
    public static final String EMAIL_STORE = "email.store";

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ConnectorConfigService configService;

    @Value("${admin.email:}")
    private String adminEmail;

    public EmailConnector(EmailService emailService, UserRepository userRepository,
                          ConnectorConfigService configService) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.configService = configService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Email";
    }

    @Override
    public ConnectorCategory category() {
        return ConnectorCategory.NOTIFICATION;
    }

    @Override
    public boolean enabled() {
        return configService.isEnabled(ID, true);
    }

    @Override
    public Set<ConnectorAction> actions() {
        return Set.of(
                new ConnectorAction(EMAIL_CUSTOMER, "Email customer"),
                new ConnectorAction(EMAIL_STORE, "Email store"));
    }

    @Override
    public ConnectorResult execute(String actionCode, OrderStageContext ctx) {
        String to;
        if (EMAIL_CUSTOMER.equals(actionCode)) {
            to = ctx.getUserId() == null ? null
                    : userRepository.findById(ctx.getUserId()).map(User::getEmail).orElse(null);
        } else if (EMAIL_STORE.equals(actionCode)) {
            to = adminEmail;
        } else {
            return ConnectorResult.fail("Unknown action " + actionCode);
        }
        if (to == null || to.isBlank()) {
            return ConnectorResult.fail("No recipient address for " + actionCode);
        }
        boolean sent = emailService.sendHtml(to, subject(ctx), body(ctx));
        return sent ? ConnectorResult.ok("Emailed " + to) : ConnectorResult.fail("Email send failed to " + to);
    }

    private String subject(OrderStageContext ctx) {
        String num = ctx.getParams() == null ? null : ctx.getParams().get("orderNumber");
        return "Order update" + (num == null ? "" : " — " + num) + " (" + ctx.getStageCode() + ")";
    }

    private String body(OrderStageContext ctx) {
        String num = ctx.getParams() == null ? null : ctx.getParams().get("orderNumber");
        return "<div style='font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#212121'>"
                + "<h2 style='color:#1A3A6D'>Order update</h2>"
                + "<p>Your order" + (num == null ? "" : " <strong>" + num + "</strong>")
                + " is now <strong>" + ctx.getStageCode() + "</strong>.</p>"
                + "<hr style='border:none;border-top:1px solid #eee'>"
                + "<p style='color:#888;font-size:12px'>This is an automated order notification.</p>"
                + "</div>";
    }
}
