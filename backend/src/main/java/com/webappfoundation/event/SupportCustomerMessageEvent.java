package com.webappfoundation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by SupportService when a CUSTOMER writes to a case — on case creation ({@code newCase=true})
 * and on every subsequent customer reply ({@code newCase=false}). {@code EmailNotificationListener}
 * listens ({@code @Async @TransactionalEventListener(AFTER_COMMIT)}) and emails the admin
 * ({@code admin.email}) so staff are alerted to incoming requests. Carries the ids/fields the listener
 * needs; the customer's name/email is resolved from {@code userId} via UserRepository.
 */
@Getter
public class SupportCustomerMessageEvent extends ApplicationEvent {

    private final Long caseId;
    private final Integer userId;
    private final String subject;
    private final String messageBody;
    private final boolean newCase;

    public SupportCustomerMessageEvent(Object source, Long caseId, Integer userId, String subject,
                                       String messageBody, boolean newCase) {
        super(source);
        this.caseId = caseId;
        this.userId = userId;
        this.subject = subject;
        this.messageBody = messageBody;
        this.newCase = newCase;
    }
}
