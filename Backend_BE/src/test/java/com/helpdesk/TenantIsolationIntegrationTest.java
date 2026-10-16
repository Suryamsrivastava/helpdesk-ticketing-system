package com.helpdesk;

import com.helpdesk.comment.Comment;
import com.helpdesk.comment.CommentRepository;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.security.JwtService;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketPriority;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack (security filter, controllers, services, JPA on H2): proves that one
 * organization can never read or change another organization's data, and that
 * customers never see internal notes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private TicketHistoryRepository historyRepository;

    private User adminA;
    private User agentA;
    private User customerA1;
    private User customerA2;
    private User agentB;
    private Ticket ticketA;
    private Ticket ticketB;

    @BeforeEach
    void seed() {
        historyRepository.deleteAll();
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization orgA = organizationRepository.save(new Organization("Org A", "org-a"));
        Organization orgB = organizationRepository.save(new Organization("Org B", "org-b"));
        String hash = passwordEncoder.encode("Password@123");

        adminA = userRepository.save(new User(orgA, "admin@a.test", hash, "Admin A", Role.ORG_ADMIN));
        agentA = userRepository.save(new User(orgA, "agent@a.test", hash, "Agent A", Role.AGENT));
        customerA1 = userRepository.save(new User(orgA, "c1@a.test", hash, "Customer A1", Role.CUSTOMER));
        customerA2 = userRepository.save(new User(orgA, "c2@a.test", hash, "Customer A2", Role.CUSTOMER));
        agentB = userRepository.save(new User(orgB, "agent@b.test", hash, "Agent B", Role.AGENT));
        User customerB = userRepository.save(new User(orgB, "c1@b.test", hash, "Customer B1", Role.CUSTOMER));

        ticketA = ticketRepository.save(new Ticket(orgA, customerA1, "VPN not connecting", "Error 809", TicketPriority.HIGH));
        ticketB = ticketRepository.save(new Ticket(orgB, customerB, "Billing question", "Wrong amount", TicketPriority.LOW));
        commentRepository.save(new Comment(ticketA, agentA, "Can you restart the client?", false));
        commentRepository.save(new Comment(ticketA, agentA, "Probably the expired cert again", true));
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void requestsWithoutTokenAreRejectedWith401() throws Exception {
        mvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void loginReturnsTokenAndWrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"agent@a.test\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.organizationName", is("Org A")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"agent@a.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agentCannotReadOrChangeAnotherOrganizationsTicket() throws Exception {
        String url = "/api/tickets/" + ticketA.getId();
        mvc.perform(as(agentB, get(url))).andExpect(status().isNotFound());
        mvc.perform(as(agentB, put(url + "/status")).content("{\"status\":\"CLOSED\"}")).andExpect(status().isNotFound());
        mvc.perform(as(agentB, put(url + "/assignee")).content("{\"assigneeId\":" + agentB.getId() + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(as(agentB, get(url + "/comments"))).andExpect(status().isNotFound());
        mvc.perform(as(agentB, post(url + "/comments")).content("{\"body\":\"hi\"}")).andExpect(status().isNotFound());
        mvc.perform(as(agentB, get(url + "/history"))).andExpect(status().isNotFound());
    }

    @Test
    void ticketListOnlyContainsOwnOrganization() throws Exception {
        mvc.perform(as(agentB, get("/api/tickets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(ticketB.getId().intValue())));
    }

    @Test
    void customerOnlySeesOwnTicketsEvenWithRequesterFilter() throws Exception {
        mvc.perform(as(customerA2, get("/api/tickets/" + ticketA.getId()))).andExpect(status().isNotFound());
        mvc.perform(as(customerA2, get("/api/tickets").param("requesterId", customerA1.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void customerNeverReceivesInternalNotes() throws Exception {
        String url = "/api/tickets/" + ticketA.getId() + "/comments";
        mvc.perform(as(customerA1, get(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].internal", everyItem(is(false))));
        mvc.perform(as(agentA, get(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(as(customerA1, post(url)).content("{\"body\":\"sneaky\",\"internal\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotUseStaffEndpoints() throws Exception {
        mvc.perform(as(customerA1, get("/api/users"))).andExpect(status().isForbidden());
        mvc.perform(as(customerA1, put("/api/tickets/" + ticketA.getId() + "/assignee"))
                        .content("{\"assigneeId\":" + agentA.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(agentA, post("/api/users"))
                        .content("{\"fullName\":\"X\",\"email\":\"x@a.test\",\"password\":\"Password@123\",\"role\":\"AGENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAssignAgentFromAnotherOrganization() throws Exception {
        mvc.perform(as(adminA, put("/api/tickets/" + ticketA.getId() + "/assignee"))
                        .content("{\"assigneeId\":" + agentB.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignMovesTicketToInProgressAndWritesHistory() throws Exception {
        String url = "/api/tickets/" + ticketA.getId();
        mvc.perform(as(adminA, put(url + "/assignee")).content("{\"assigneeId\":" + agentA.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id", is(agentA.getId().intValue())))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
        mvc.perform(as(customerA1, get(url + "/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", hasItem("ASSIGNEE_CHANGED")))
                .andExpect(jsonPath("$[*].type", hasItem("STATUS_CHANGED")));
    }

    @Test
    void adminOnlyManagesUsersOfOwnOrganization() throws Exception {
        mvc.perform(as(adminA, get("/api/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(4)));
        mvc.perform(as(adminA, patch("/api/users/" + agentB.getId())).content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidInputReturns400WithFieldErrors() throws Exception {
        mvc.perform(as(customerA1, post("/api/tickets")).content("{\"subject\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("subject")));
        mvc.perform(as(agentA, get("/api/tickets").param("status", "NOT_A_STATUS")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(agentA, get("/api/tickets").param("sort", "description,asc")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registeringAnOrganizationCreatesAnAdminAndAToken() throws Exception {
        mvc.perform(post("/api/auth/register-organization").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Initech\",\"adminName\":\"Bill\","
                                + "\"email\":\"bill@initech.test\",\"password\":\"Password@123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role", is("ORG_ADMIN")))
                .andExpect(jsonPath("$.accessToken", notNullValue()));

        mvc.perform(post("/api/auth/register-organization").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Other\",\"adminName\":\"Dup\","
                                + "\"email\":\"agent@a.test\",\"password\":\"Password@123\"}"))
                .andExpect(status().isConflict());
    }
}
