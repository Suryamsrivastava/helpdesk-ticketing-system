package com.helpdesk.ticket;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Query pieces for the ticket search. {@link #inOrganization(Long)} is always applied
 * by the service, so no filter combination can reach another tenant's rows.
 */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> inOrganization(Long organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    public static Specification<Ticket> requestedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
    }

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasPriority(TicketPriority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> assignedTo(Long assigneeId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    /** Email tickets still waiting for an agent to link them to a customer. */
    public static Specification<Ticket> withoutRequester() {
        return (root, query, cb) -> cb.isNull(root.get("requester"));
    }

    public static Specification<Ticket> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignee"));
    }

    /** Subject match, or an exact ticket id when the text is a number. */
    public static Specification<Ticket> matchesText(String text) {
        String trimmed = text.trim();
        String pattern = "%" + trimmed.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> {
            var subjectMatch = cb.like(cb.lower(root.get("subject")), pattern, '\\');
            if (trimmed.matches("\\d{1,18}")) {
                return cb.or(subjectMatch, cb.equal(root.get("id"), Long.valueOf(trimmed)));
            }
            return subjectMatch;
        };
    }
}
