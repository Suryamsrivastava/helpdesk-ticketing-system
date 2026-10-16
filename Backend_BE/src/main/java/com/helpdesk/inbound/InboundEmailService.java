package com.helpdesk.inbound;

import com.helpdesk.comment.Comment;
import com.helpdesk.comment.CommentRepository;
import com.helpdesk.common.exception.ApiException;
import com.helpdesk.history.HistoryType;
import com.helpdesk.history.TicketHistory;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.inbound.InboundEmailDtos.InboundEmailRequest;
import com.helpdesk.inbound.InboundEmailDtos.InboundEmailResult;
import com.helpdesk.inbound.InboundEmailDtos.Outcome;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketStatus;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Turns a mail sent to an organization's helpdesk address into a ticket.
 *
 * <ul>
 *   <li>The organization is found by the inbound key only, never by anything in the mail.</li>
 *   <li>The sender is matched against customers of THAT organization. A match becomes the
 *       requester; any other address still opens a ticket, left unlinked for an agent to map.</li>
 *   <li>One mail thread = one ticket: later mails on the thread are added as comments.</li>
 *   <li>A message id is processed once, so the script can safely retry.</li>
 * </ul>
 */
@Service
public class InboundEmailService {

    static final int MAX_SUBJECT = 200;
    static final int MAX_BODY = 10000;

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;

    public InboundEmailService(OrganizationRepository organizationRepository, UserRepository userRepository,
                               TicketRepository ticketRepository, CommentRepository commentRepository,
                               TicketHistoryRepository historyRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public InboundEmailResult receive(String inboundKey, InboundEmailRequest request) {
        if (inboundKey == null || inboundKey.isBlank()) {
            throw ApiException.unauthorized("Missing X-Inbound-Key header");
        }
        Organization org = organizationRepository.findByInboundKey(inboundKey.trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid inbound key"));

        String senderEmail = request.fromEmail().trim().toLowerCase(Locale.ROOT);
        String senderName = blankToNull(request.fromName());
        String messageId = blankToNull(request.messageId());
        String threadId = request.threadId().trim();
        String body = fit(request.body(), MAX_BODY, "(no message body)");

        if (messageId != null && alreadyProcessed(org.getId(), messageId)) {
            return new InboundEmailResult(Outcome.DUPLICATE, null, false);
        }

        // only users of THIS organization can match; the same address in another tenant is a stranger here
        User sender = userRepository.findByEmailIgnoreCase(senderEmail)
                .filter(u -> u.getOrganization().getId().equals(org.getId()) && u.isActive())
                .orElse(null);

        Optional<Ticket> existing = ticketRepository.findByOrganizationIdAndEmailThreadId(org.getId(), threadId);
        if (existing.isPresent()) {
            Ticket ticket = existing.get();
            commentRepository.save(Comment.fromEmail(ticket, sender, body, senderEmail, senderName, messageId));
            return new InboundEmailResult(Outcome.COMMENT_ADDED, ticket.getId(), ticket.getRequester() != null);
        }

        // only a registered customer is linked automatically; staff or unknown senders wait for an agent
        User requester = sender != null && sender.getRole() == Role.CUSTOMER ? sender : null;
        Ticket ticket = ticketRepository.save(Ticket.fromEmail(org, requester,
                fit(request.subject(), MAX_SUBJECT, "(no subject)"), body,
                senderEmail, senderName, threadId, messageId));
        historyRepository.save(new TicketHistory(ticket, requester, HistoryType.CREATED, null, TicketStatus.OPEN.name()));
        return new InboundEmailResult(Outcome.TICKET_CREATED, ticket.getId(), requester != null);
    }

    private boolean alreadyProcessed(Long organizationId, String messageId) {
        return ticketRepository.existsByOrganizationIdAndEmailMessageId(organizationId, messageId)
                || commentRepository.existsByTicket_Organization_IdAndEmailMessageId(organizationId, messageId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Trims, falls back when empty, and cuts to the column size instead of rejecting the mail. */
    static String fit(String value, int max, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
