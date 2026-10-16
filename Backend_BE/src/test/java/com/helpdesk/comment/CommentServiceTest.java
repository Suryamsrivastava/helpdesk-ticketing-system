package com.helpdesk.comment;

import com.helpdesk.comment.CommentDtos.CreateCommentRequest;
import com.helpdesk.common.exception.ApiException;
import com.helpdesk.organization.Organization;
import com.helpdesk.security.AuthUser;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketService;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static com.helpdesk.support.TestData.org;
import static com.helpdesk.support.TestData.ticket;
import static com.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    private User agent;
    private User customer;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        Organization acme = org(1);
        agent = user(acme, 10, Role.AGENT);
        customer = user(acme, 20, Role.CUSTOMER);
        ticket = ticket(acme, 5, customer);
    }

    @Test
    void customerOnlyGetsPublicCommentsFromTheQuery() {
        AuthUser me = AuthUser.from(customer);
        when(ticketService.requireAccessible(me, 5L)).thenReturn(ticket);
        when(commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(5L))
                .thenReturn(List.of(new Comment(ticket, agent, "We are on it", false)));

        assertThat(commentService.list(me, 5L)).extracting(CommentDtos.CommentResponse::internal).containsOnly(false);
        verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAscIdAsc(anyLong());
    }

    @Test
    void agentGetsInternalNotesToo() {
        AuthUser me = AuthUser.from(agent);
        when(ticketService.requireAccessible(me, 5L)).thenReturn(ticket);
        when(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(5L)).thenReturn(List.of(
                new Comment(ticket, customer, "Help!", false),
                new Comment(ticket, agent, "Known bug", true)));

        assertThat(commentService.list(me, 5L)).extracting(CommentDtos.CommentResponse::internal)
                .containsExactly(false, true);
    }

    @Test
    void customerCannotWriteInternalNote() {
        AuthUser me = AuthUser.from(customer);
        when(ticketService.requireAccessible(me, 5L)).thenReturn(ticket);

        assertThatThrownBy(() -> commentService.add(me, 5L, new CreateCommentRequest("secret", true)))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void agentCanWriteInternalNote() {
        AuthUser me = AuthUser.from(agent);
        when(ticketService.requireAccessible(me, 5L)).thenReturn(ticket);
        when(userRepository.findByIdAndOrganizationId(10L, 1L)).thenReturn(Optional.of(agent));
        when(commentRepository.save(any(Comment.class))).then(returnsFirstArg());

        var saved = commentService.add(me, 5L, new CreateCommentRequest("  Escalated to L2  ", true));

        assertThat(saved.internal()).isTrue();
        assertThat(saved.body()).isEqualTo("Escalated to L2");
        assertThat(saved.author().id()).isEqualTo(10L);
    }
}
