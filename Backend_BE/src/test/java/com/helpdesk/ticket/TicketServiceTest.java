package com.helpdesk.ticket;

import com.helpdesk.common.exception.ApiException;
import com.helpdesk.history.HistoryType;
import com.helpdesk.history.TicketHistory;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.security.AuthUser;
import com.helpdesk.ticket.dto.TicketDtos.AssignTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.ChangeStatusRequest;
import com.helpdesk.ticket.dto.TicketDtos.CreateTicketRequest;
import com.helpdesk.ticket.dto.TicketDtos.TicketResponse;
import com.helpdesk.ticket.dto.TicketDtos.UpdateTicketRequest;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TicketHistoryRepository historyRepository;

    @InjectMocks
    private TicketService ticketService;

    private Organization acme;
    private User agent;
    private User customer;
    private User otherCustomer;

    @BeforeEach
    void setUp() {
        acme = org(1);
        agent = user(acme, 10, Role.AGENT);
        customer = user(acme, 20, Role.CUSTOMER);
        otherCustomer = user(acme, 21, Role.CUSTOMER);
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(expected);
    }

    @Nested
    @DisplayName("Tenant and ownership isolation")
    class Isolation {

        @Test
        void ticketOfAnotherOrganizationIsNotFound() {
            // the repository is always asked with the caller's org id; another tenant's ticket never matches
            when(ticketRepository.findByIdAndOrganizationId(99L, 1L)).thenReturn(Optional.empty());

            assertStatus(() -> ticketService.get(AuthUser.from(agent), 99L), HttpStatus.NOT_FOUND);
            verify(ticketRepository).findByIdAndOrganizationId(99L, 1L);
        }

        @Test
        void customerCannotOpenAnotherCustomersTicket() {
            Ticket othersTicket = ticket(acme, 5, otherCustomer);
            when(ticketRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(othersTicket));

            assertStatus(() -> ticketService.get(AuthUser.from(customer), 5L), HttpStatus.NOT_FOUND);
        }

        @Test
        void customerCanOpenOwnTicket() {
            Ticket own = ticket(acme, 6, customer);
            when(ticketRepository.findByIdAndOrganizationId(6L, 1L)).thenReturn(Optional.of(own));

            assertThat(ticketService.get(AuthUser.from(customer), 6L).id()).isEqualTo(6L);
        }
    }

    @Nested
    @DisplayName("Creating tickets")
    class Create {

        @BeforeEach
        void stubSave() {
            when(organizationRepository.getReferenceById(1L)).thenReturn(acme);
            when(ticketRepository.save(any(Ticket.class))).then(returnsFirstArg());
        }

        @Test
        void customerTicketIsAlwaysRequestedByTheCustomer() {
            when(userRepository.findByIdAndOrganizationId(20L, 1L)).thenReturn(Optional.of(customer));

            TicketResponse created = ticketService.create(AuthUser.from(customer),
                    new CreateTicketRequest("Printer jammed", "It shows error E4", null, null));

            assertThat(created.requester().id()).isEqualTo(20L);
            assertThat(created.status()).isEqualTo(TicketStatus.OPEN);
            assertThat(created.priority()).isEqualTo(TicketPriority.MEDIUM);
            ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getType()).isEqualTo(HistoryType.CREATED);
        }

        @Test
        void agentCanRaiseTicketForCustomerOfSameOrganization() {
            when(userRepository.findByIdAndOrganizationId(10L, 1L)).thenReturn(Optional.of(agent));
            when(userRepository.findByIdAndOrganizationId(20L, 1L)).thenReturn(Optional.of(customer));

            TicketResponse created = ticketService.create(AuthUser.from(agent),
                    new CreateTicketRequest("Phone call", "Customer called about billing", TicketPriority.HIGH, 20L));

            assertThat(created.requester().id()).isEqualTo(20L);
            assertThat(created.priority()).isEqualTo(TicketPriority.HIGH);
        }
    }

    @Test
    void customerCannotRaiseTicketForSomeoneElse() {
        when(userRepository.findByIdAndOrganizationId(20L, 1L)).thenReturn(Optional.of(customer));

        assertStatus(() -> ticketService.create(AuthUser.from(customer),
                new CreateTicketRequest("x", "y", null, 21L)), HttpStatus.FORBIDDEN);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void agentCannotRaiseTicketForUserOutsideTheOrganization() {
        when(userRepository.findByIdAndOrganizationId(10L, 1L)).thenReturn(Optional.of(agent));
        when(userRepository.findByIdAndOrganizationId(500L, 1L)).thenReturn(Optional.empty());

        assertStatus(() -> ticketService.create(AuthUser.from(agent),
                new CreateTicketRequest("x", "y", null, 500L)), HttpStatus.BAD_REQUEST);
    }

    @Nested
    @DisplayName("Assignment")
    class Assignment {

        @Test
        void assigningAnOpenTicketStartsWorkAndRecordsHistory() {
            Ticket open = ticket(acme, 7, customer);
            when(ticketRepository.findByIdAndOrganizationId(7L, 1L)).thenReturn(Optional.of(open));
            when(userRepository.findByIdAndOrganizationId(10L, 1L)).thenReturn(Optional.of(agent));

            TicketResponse result = ticketService.assign(AuthUser.from(agent), 7L, new AssignTicketRequest(10L));

            assertThat(result.assignee().id()).isEqualTo(10L);
            assertThat(result.status()).isEqualTo(TicketStatus.IN_PROGRESS);
            ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
            verify(historyRepository, times(2)).save(history.capture());
            assertThat(history.getAllValues()).extracting(TicketHistory::getType)
                    .containsExactly(HistoryType.ASSIGNEE_CHANGED, HistoryType.STATUS_CHANGED);
        }

        @Test
        void assigneeMustBeAnAgent() {
            Ticket open = ticket(acme, 7, customer);
            when(ticketRepository.findByIdAndOrganizationId(7L, 1L)).thenReturn(Optional.of(open));
            when(userRepository.findByIdAndOrganizationId(21L, 1L)).thenReturn(Optional.of(otherCustomer));

            assertStatus(() -> ticketService.assign(AuthUser.from(agent), 7L, new AssignTicketRequest(21L)),
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        void agentFromAnotherOrganizationCannotBeAssigned() {
            Ticket open = ticket(acme, 7, customer);
            when(ticketRepository.findByIdAndOrganizationId(7L, 1L)).thenReturn(Optional.of(open));
            // agent 300 exists, but in another org, so the org-scoped lookup finds nothing
            when(userRepository.findByIdAndOrganizationId(300L, 1L)).thenReturn(Optional.empty());

            assertStatus(() -> ticketService.assign(AuthUser.from(agent), 7L, new AssignTicketRequest(300L)),
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        void customerCannotAssign() {
            assertStatus(() -> ticketService.assign(AuthUser.from(customer), 7L, new AssignTicketRequest(10L)),
                    HttpStatus.FORBIDDEN);
            verify(ticketRepository, never()).findByIdAndOrganizationId(any(), any());
        }
    }

    @Nested
    @DisplayName("Status changes")
    class Status {

        @Test
        void closedTicketCannotJumpToResolved() {
            Ticket closed = ticket(acme, 8, customer);
            closed.changeStatus(TicketStatus.CLOSED);
            when(ticketRepository.findByIdAndOrganizationId(8L, 1L)).thenReturn(Optional.of(closed));

            assertStatus(() -> ticketService.changeStatus(AuthUser.from(agent), 8L,
                    new ChangeStatusRequest(TicketStatus.RESOLVED)), HttpStatus.CONFLICT);
        }

        @Test
        void customerCannotResolveTheirOwnTicket() {
            Ticket own = ticket(acme, 9, customer);
            when(ticketRepository.findByIdAndOrganizationId(9L, 1L)).thenReturn(Optional.of(own));

            assertStatus(() -> ticketService.changeStatus(AuthUser.from(customer), 9L,
                    new ChangeStatusRequest(TicketStatus.RESOLVED)), HttpStatus.FORBIDDEN);
        }

        @Test
        void closingStampsTimesAndRecordsHistory() {
            Ticket inProgress = ticket(acme, 11, customer);
            inProgress.changeStatus(TicketStatus.IN_PROGRESS);
            when(ticketRepository.findByIdAndOrganizationId(11L, 1L)).thenReturn(Optional.of(inProgress));
            when(userRepository.findByIdAndOrganizationId(20L, 1L)).thenReturn(Optional.of(customer));

            TicketResponse result = ticketService.changeStatus(AuthUser.from(customer), 11L,
                    new ChangeStatusRequest(TicketStatus.CLOSED));

            assertThat(result.status()).isEqualTo(TicketStatus.CLOSED);
            assertThat(result.closedAt()).isNotNull();
            ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(List.of(history.getValue().getFromValue(), history.getValue().getToValue()))
                    .containsExactly("IN_PROGRESS", "CLOSED");
        }
    }

    @Nested
    @DisplayName("Linking email tickets to a customer")
    class Linking {

        @Test
        void agentLinksUnlinkedEmailTicketAndHistoryKeepsTheSenderAddress() {
            Ticket fromEmail = Ticket.fromEmail(acme, null, "Broken chair", "Arrived damaged",
                    "personal@gmail.com", "Riya", "thread-1", "msg-1");
            org.springframework.test.util.ReflectionTestUtils.setField(fromEmail, "id", 30L);
            when(ticketRepository.findByIdAndOrganizationId(30L, 1L)).thenReturn(Optional.of(fromEmail));
            when(userRepository.findByIdAndOrganizationId(20L, 1L)).thenReturn(Optional.of(customer));
            when(userRepository.findByIdAndOrganizationId(10L, 1L)).thenReturn(Optional.of(agent));

            TicketResponse result = ticketService.linkRequester(AuthUser.from(agent), 30L,
                    new com.helpdesk.ticket.dto.TicketDtos.LinkRequesterRequest(20L));

            assertThat(result.requester().id()).isEqualTo(20L);
            ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getType()).isEqualTo(HistoryType.REQUESTER_CHANGED);
            assertThat(history.getValue().getFromValue()).isEqualTo("personal@gmail.com");
        }

        @Test
        void onlyCustomersCanBeLinked() {
            Ticket fromEmail = Ticket.fromEmail(acme, null, "s", "d", "x@gmail.com", null, "t", "m");
            when(ticketRepository.findByIdAndOrganizationId(31L, 1L)).thenReturn(Optional.of(fromEmail));
            when(userRepository.findByIdAndOrganizationId(10L, 1L)).thenReturn(Optional.of(agent));

            assertStatus(() -> ticketService.linkRequester(AuthUser.from(agent), 31L,
                    new com.helpdesk.ticket.dto.TicketDtos.LinkRequesterRequest(10L)), HttpStatus.BAD_REQUEST);
        }

        @Test
        void customerCannotSeeAnUnlinkedTicket() {
            Ticket fromEmail = Ticket.fromEmail(acme, null, "s", "d", "x@gmail.com", null, "t", "m");
            when(ticketRepository.findByIdAndOrganizationId(32L, 1L)).thenReturn(Optional.of(fromEmail));

            assertStatus(() -> ticketService.get(AuthUser.from(customer), 32L), HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void customerCannotChangePriority() {
        Ticket own = ticket(acme, 12, customer);
        when(ticketRepository.findByIdAndOrganizationId(12L, 1L)).thenReturn(Optional.of(own));

        assertStatus(() -> ticketService.update(AuthUser.from(customer), 12L,
                new UpdateTicketRequest(null, null, TicketPriority.URGENT)), HttpStatus.FORBIDDEN);
    }
}
