package com.webappfoundation.event;

import com.webappfoundation.model.SupportStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by SupportService when STAFF replies to a support case. {@code EmailNotificationListener}
 * listens ({@code @Async @TransactionalEventListener(AFTER_COMMIT)}) and emails the owning customer.
 * Carries the ids/fields the listener needs; the customer's email is resolved from {@code userId} via
 * UserRepository so no email is stored on the case.
 */
@Getter
public class SupportRepliedEvent extends ApplicationEvent {

    private final Long caseId;
    private final Integer userId;
    private final String subject;
    private final String replyBody;
    private final SupportStatus status;

    public SupportRepliedEvent(Object source, Long caseId, Integer userId, String subject,
                               String replyBody, SupportStatus status) {
        super(source);
        this.caseId = caseId;
        this.userId = userId;
        this.subject = subject;
        this.replyBody = replyBody;
        this.status = status;
    }
}
