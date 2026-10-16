package com.helpdesk.ticket.dto;

import com.helpdesk.history.HistoryType;
import com.helpdesk.history.TicketHistory;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketChannel;
import com.helpdesk.ticket.TicketPriority;
import com.helpdesk.ticket.TicketStatus;
import com.helpdesk.user.dto.UserDtos.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class TicketDtos {

    private TicketDtos() {
    }

    /**
     * {@code requester} is null for an email ticket that is not linked to a customer yet;
     * {@code senderEmail}/{@code senderName} then say who wrote in.
     */
    public record TicketResponse(
            Long id,
            String subject,
            String description,
            TicketPriority priority,
            TicketStatus status,
            TicketChannel channel,
            UserRef requester,
            String senderEmail,
            String senderName,
            UserRef assignee,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            Instant closedAt
    ) {

        public static TicketResponse of(Ticket t) {
            return new TicketResponse(t.getId(), t.getSubject(), t.getDescription(), t.getPriority(), t.getStatus(),
                    t.getChannel(), UserRef.of(t.getRequester()), t.getSenderEmail(), t.getSenderName(),
                    UserRef.of(t.getAssignee()), t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt(), t.getClosedAt());
        }
    }

    /**
     * {@code requesterId} lets staff raise a ticket on behalf of a customer; customers
     * leave it empty (the ticket is always theirs).
     */
    public record CreateTicketRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 10000) String description,
            TicketPriority priority,
            Long requesterId
    ) {
    }

    /** Any field left null is unchanged. Customers may not change priority. */
    public record UpdateTicketRequest(
            @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String subject,
            @Size(max = 10000) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String description,
            TicketPriority priority
    ) {
    }

    /** {@code assigneeId = null} unassigns the ticket. */
    public record AssignTicketRequest(Long assigneeId) {
    }

    public record ChangeStatusRequest(@NotNull TicketStatus status) {
    }

    /** Links a ticket (typically one that came in by email) to a customer of the organization. */
    public record LinkRequesterRequest(@NotNull Long requesterId) {
    }

    public record HistoryResponse(Long id, HistoryType type, String fromValue, String toValue,
                                  UserRef actor, Instant createdAt) {

        public static HistoryResponse of(TicketHistory h) {
            return new HistoryResponse(h.getId(), h.getType(), h.getFromValue(), h.getToValue(),
                    UserRef.of(h.getActor()), h.getCreatedAt());
        }
    }

    /** Search filters; all optional and combined with AND. */
    public record TicketFilter(
            TicketStatus status,
            TicketPriority priority,
            Long assigneeId,
            Boolean unassigned,
            Long requesterId,
            Boolean unlinked,
            String q
    ) {
    }
}
