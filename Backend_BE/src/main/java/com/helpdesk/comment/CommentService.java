package com.helpdesk.comment;

import com.helpdesk.comment.CommentDtos.CommentResponse;
import com.helpdesk.comment.CommentDtos.CreateCommentRequest;
import com.helpdesk.common.exception.ApiException;
import com.helpdesk.security.AuthUser;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketService;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Comments and internal notes. Customers never receive internal notes: they are
 * excluded in the database query, not filtered in the UI.
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, TicketService ticketService, UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(AuthUser me, Long ticketId) {
        Ticket ticket = ticketService.requireAccessible(me, ticketId);
        List<Comment> comments = me.isCustomer()
                ? commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(ticket.getId())
                : commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        return comments.stream().map(CommentResponse::of).toList();
    }

    @Transactional
    public CommentResponse add(AuthUser me, Long ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketService.requireAccessible(me, ticketId);
        if (request.internal() && !me.isStaff()) {
            throw ApiException.forbidden("Only agents and admins can add internal notes");
        }
        User author = userRepository.findByIdAndOrganizationId(me.id(), me.organizationId())
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        Comment comment = commentRepository.save(new Comment(ticket, author, request.body().trim(), request.internal()));
        return CommentResponse.of(comment);
    }
}
