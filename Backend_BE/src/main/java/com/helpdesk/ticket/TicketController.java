package com.helpdesk.ticket;

import com.helpdesk.common.PageResponse;
import com.helpdesk.security.AuthUser;
import com.helpdesk.ticket.dto.TicketDtos.AssignTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.ChangeStatusRequest;
import com.helpdesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.HistoryResponse;
import com.helpdesk.ticket.dto.TicketDtos.LinkRequesterRequest;
import com.helpdesk.ticket.dto.TicketDtos.TicketFilter;
import com.helpdesk.ticket.dto.TicketDtos.TicketResponse;
import com.helpdesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@Validated
@Tag(name = "Tickets", description = "Tickets of the caller's organization (customers: only their own)")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @Operation(summary = "Raise a ticket. Staff may pass requesterId to raise it for a customer.")
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal AuthUser me,
                                                 @Valid @RequestBody CreateTicketRequest request) {
        TicketResponse created = ticketService.create(me, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Search and filter tickets with pagination",
            description = "Sort by createdAt, updatedAt, id or subject, e.g. sort=createdAt,desc")
    public PageResponse<TicketResponse> search(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long assigneeId,
            @Parameter(description = "true = only tickets with no assignee")
            @RequestParam(required = false) Boolean unassigned,
            @Parameter(description = "Staff only; ignored for customers")
            @RequestParam(required = false) Long requesterId,
            @Parameter(description = "Staff only: true = email tickets not linked to a customer yet")
            @RequestParam(required = false) Boolean unlinked,
            @Parameter(description = "Text in the subject, or a ticket id")
            @RequestParam(required = false) @Size(max = 100) String q,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ticketService.search(me,
                new TicketFilter(status, priority, assigneeId, unassigned, requesterId, unlinked, q), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ticket details")
    public TicketResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ticketService.get(me, id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit subject, description or priority (customers: not priority)")
    public TicketResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                 @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(me, id, request);
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "Assign to an agent, or unassign with assigneeId = null (admin, agent)")
    public TicketResponse assign(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                 @RequestBody AssignTicketRequest request) {
        return ticketService.assign(me, id, request);
    }

    @PutMapping("/{id}/requester")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "Link the ticket to a customer, e.g. a mail from an unregistered address (admin, agent)")
    public TicketResponse linkRequester(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                        @Valid @RequestBody LinkRequesterRequest request) {
        return ticketService.linkRequester(me, id, request);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change status, e.g. CLOSED to close (customers: close or reopen only)")
    public TicketResponse changeStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody ChangeStatusRequest request) {
        return ticketService.changeStatus(me, id, request);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Status, assignment and edit history")
    public List<HistoryResponse> history(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ticketService.history(me, id);
    }
}
