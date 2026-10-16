package com.helpdesk.inbound;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class InboundEmailDtos {

    private InboundEmailDtos() {
    }

    /**
     * One mail as the intake script read it from the helpdesk mailbox. Long subjects and
     * bodies are truncated rather than rejected, so no customer mail is ever lost.
     */
    public record InboundEmailRequest(
            @NotBlank @Size(max = 200) String threadId,
            @Size(max = 250) String messageId,
            @NotBlank @Email @Size(max = 160) String fromEmail,
            @Size(max = 120) String fromName,
            @Size(max = 2000) String subject,
            @Size(max = 200000) String body
    ) {
    }

    public enum Outcome {
        /** First mail of a thread: a new ticket was opened. */
        TICKET_CREATED,
        /** A reply on a thread that already has a ticket: added as a comment. */
        COMMENT_ADDED,
        /** This message id was already processed; nothing changed. */
        DUPLICATE
    }

    /** {@code linkedToCustomer = false} means the ticket waits for an agent to link it. */
    public record InboundEmailResult(Outcome outcome, Long ticketId, boolean linkedToCustomer) {
    }
}
