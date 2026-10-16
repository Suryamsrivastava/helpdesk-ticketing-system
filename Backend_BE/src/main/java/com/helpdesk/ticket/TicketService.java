package com.helpdesk.ticket;

import com.helpdesk.common.PageResponse;
import com.helpdesk.common.SortGuard;
import com.helpdesk.common.exception.ApiException;
import com.helpdesk.history.HistoryType;
import com.helpdesk.history.TicketHistory;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.security.AuthUser;
import com.helpdesk.ticket.dto.TicketDtos.AssignTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.ChangeStatusRequest;
import com.helpdesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.HistoryResponse;
import com.helpdesk.ticket.dto.TicketDtos.LinkRequesterRequest;
import com.helpdesk.ticket.dto.TicketDtos.TicketFilter;
import com.helpdesk.ticket.dto.TicketDtos.TicketResponse;
import com.helpdesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * All ticket rules live here. Tenant isolation rule: every ticket is loaded by
 * (id, organizationId of the caller), and customers additionally only by their own
 * requester id. A ticket outside that scope is reported as 404, never 403, so its
 * existence is not leaked across tenants.
 */
@Service
public class TicketService {

    private static final Set<String> TICKET_SORTS = Set.of("createdAt", "updatedAt", "id", "subject");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketHistoryRepository historyRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository,
                         OrganizationRepository organizationRepository, TicketHistoryRepository historyRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public TicketResponse create(AuthUser me, CreateTicketRequest request) {
        User actor = currentUser(me);
        User requester = resolveRequester(me, actor, request.requesterId());
        TicketPriority priority = request.priority() != null ? request.priority() : TicketPriority.MEDIUM;

        Ticket ticket = ticketRepository.save(new Ticket(
                organizationRepository.getReferenceById(me.organizationId()), requester,
                request.subject().trim(), request.description().trim(), priority));
        record(ticket, actor, HistoryType.CREATED, null, TicketStatus.OPEN.name());
        return TicketResponse.of(ticket);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> search(AuthUser me, TicketFilter filter, Pageable pageable) {
        SortGuard.check(pageable, TICKET_SORTS);

        Specification<Ticket> spec = TicketSpecifications.inOrganization(me.organizationId());
        if (me.isCustomer()) {
            // a customer only ever sees their own tickets, whatever filters are sent
            spec = spec.and(TicketSpecifications.requestedBy(me.id()));
        } else if (Boolean.TRUE.equals(filter.unlinked())) {
            spec = spec.and(TicketSpecifications.withoutRequester());
        } else if (filter.requesterId() != null) {
            spec = spec.and(TicketSpecifications.requestedBy(filter.requesterId()));
        }
        if (filter.status() != null) {
            spec = spec.and(TicketSpecifications.hasStatus(filter.status()));
        }
        if (filter.priority() != null) {
            spec = spec.and(TicketSpecifications.hasPriority(filter.priority()));
        }
        if (Boolean.TRUE.equals(filter.unassigned())) {
            spec = spec.and(TicketSpecifications.unassigned());
        } else if (filter.assigneeId() != null) {
            spec = spec.and(TicketSpecifications.assignedTo(filter.assigneeId()));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            spec = spec.and(TicketSpecifications.matchesText(filter.q()));
        }
        return PageResponse.from(ticketRepository.findAll(spec, pageable), TicketResponse::of);
    }

    @Transactional(readOnly = true)
    public TicketResponse get(AuthUser me, Long ticketId) {
        return TicketResponse.of(requireAccessible(me, ticketId));
    }

    @Transactional
    public TicketResponse update(AuthUser me, Long ticketId, UpdateTicketRequest request) {
        Ticket ticket = requireAccessible(me, ticketId);
        if (request.subject() == null && request.description() == null && request.priority() == null) {
            throw ApiException.badRequest("Nothing to update: send subject, description or priority");
        }
        if (me.isCustomer() && request.priority() != null && request.priority() != ticket.getPriority()) {
            throw ApiException.forbidden("Customers cannot change the priority of a ticket");
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("A closed ticket cannot be edited. Reopen it first.");
        }
        User actor = currentUser(me);

        boolean detailsChanged = false;
        if (request.subject() != null && !request.subject().trim().equals(ticket.getSubject())) {
            ticket.setSubject(request.subject().trim());
            detailsChanged = true;
        }
        if (request.description() != null && !request.description().trim().equals(ticket.getDescription())) {
            ticket.setDescription(request.description().trim());
            detailsChanged = true;
        }
        if (detailsChanged) {
            record(ticket, actor, HistoryType.DETAILS_UPDATED, null, null);
        }
        if (request.priority() != null && request.priority() != ticket.getPriority()) {
            record(ticket, actor, HistoryType.PRIORITY_CHANGED, ticket.getPriority().name(), request.priority().name());
            ticket.setPriority(request.priority());
        }
        return TicketResponse.of(ticket);
    }

    @Transactional
    public TicketResponse assign(AuthUser me, Long ticketId, AssignTicketRequest request) {
        if (!me.isStaff()) {
            throw ApiException.forbidden("Only agents and admins can assign tickets");
        }
        Ticket ticket = requireAccessible(me, ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("A closed ticket cannot be reassigned. Reopen it first.");
        }

        User newAssignee = null;
        if (request.assigneeId() != null) {
            newAssignee = userRepository.findByIdAndOrganizationId(request.assigneeId(), me.organizationId())
                    .filter(u -> u.getRole() == Role.AGENT && u.isActive())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Assignee must be an active support agent in your organization"));
        }
        User previous = ticket.getAssignee();
        if (Objects.equals(idOf(previous), idOf(newAssignee))) {
            return TicketResponse.of(ticket);
        }

        User actor = currentUser(me);
        ticket.setAssignee(newAssignee);
        record(ticket, actor, HistoryType.ASSIGNEE_CHANGED, nameOf(previous), nameOf(newAssignee));

        // picking up an open ticket starts work on it
        if (newAssignee != null && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.changeStatus(TicketStatus.IN_PROGRESS);
            record(ticket, actor, HistoryType.STATUS_CHANGED, TicketStatus.OPEN.name(), TicketStatus.IN_PROGRESS.name());
        }
        return TicketResponse.of(ticket);
    }

    @Transactional
    public TicketResponse changeStatus(AuthUser me, Long ticketId, ChangeStatusRequest request) {
        Ticket ticket = requireAccessible(me, ticketId);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();
        if (from == to) {
            return TicketResponse.of(ticket);
        }
        if (!TicketStatusPolicy.canTransition(from, to)) {
            throw ApiException.conflict("A ticket cannot move from " + from + " to " + to);
        }
        if (me.isCustomer() && !TicketStatusPolicy.customerMay(from, to)) {
            throw ApiException.forbidden("Customers can only close their ticket or reopen a resolved one");
        }
        ticket.changeStatus(to);
        record(ticket, currentUser(me), HistoryType.STATUS_CHANGED, from.name(), to.name());
        return TicketResponse.of(ticket);
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(AuthUser me, Long ticketId) {
        Ticket ticket = requireAccessible(me, ticketId);
        return historyRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                .map(HistoryResponse::of).toList();
    }

    /**
     * Loads a ticket the caller may see, or throws 404. Shared with the comment service
     * so both apply the exact same tenant and ownership rule.
     */
    @Transactional(readOnly = true)
    public Ticket requireAccessible(AuthUser me, Long ticketId) {
        Ticket ticket = ticketRepository.findByIdAndOrganizationId(ticketId, me.organizationId())
                .orElseThrow(() -> ApiException.notFound("Ticket not found"));
        // an unlinked email ticket has no requester, so no customer can see it yet
        if (me.isCustomer() && (ticket.getRequester() == null || !ticket.getRequester().getId().equals(me.id()))) {
            throw ApiException.notFound("Ticket not found");
        }
        return ticket;
    }

    /**
     * Maps a ticket to a customer of the caller's organization. Used for mails that came
     * from an address no customer is registered with; relinking a wrong match is allowed too.
     */
    @Transactional
    public TicketResponse linkRequester(AuthUser me, Long ticketId, LinkRequesterRequest request) {
        if (!me.isStaff()) {
            throw ApiException.forbidden("Only agents and admins can link a ticket to a customer");
        }
        Ticket ticket = requireAccessible(me, ticketId);
        User customer = userRepository.findByIdAndOrganizationId(request.requesterId(), me.organizationId())
                .filter(u -> u.getRole() == Role.CUSTOMER && u.isActive())
                .orElseThrow(() -> ApiException.badRequest("Requester must be an active customer in your organization"));
        User previous = ticket.getRequester();
        if (previous != null && previous.getId().equals(customer.getId())) {
            return TicketResponse.of(ticket);
        }

        String from = previous != null ? previous.getFullName()
                : ticket.getSenderEmail() != null ? ticket.getSenderEmail() : null;
        ticket.setRequester(customer);
        record(ticket, currentUser(me), HistoryType.REQUESTER_CHANGED, from, customer.getFullName());
        return TicketResponse.of(ticket);
    }

    private User resolveRequester(AuthUser me, User actor, Long requesterId) {
        if (me.isCustomer()) {
            if (requesterId != null && !requesterId.equals(me.id())) {
                throw ApiException.forbidden("Customers can only raise tickets for themselves");
            }
            return actor;
        }
        if (requesterId == null || requesterId.equals(me.id())) {
            return actor;
        }
        return userRepository.findByIdAndOrganizationId(requesterId, me.organizationId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.badRequest("Requester must be an active user in your organization"));
    }

    private User currentUser(AuthUser me) {
        return userRepository.findByIdAndOrganizationId(me.id(), me.organizationId())
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }

    private void record(Ticket ticket, User actor, HistoryType type, String from, String to) {
        historyRepository.save(new TicketHistory(ticket, actor, type, from, to));
    }

    private static Long idOf(User user) {
        return user == null ? null : user.getId();
    }

    private static String nameOf(User user) {
        return user == null ? null : user.getFullName();
    }
}
