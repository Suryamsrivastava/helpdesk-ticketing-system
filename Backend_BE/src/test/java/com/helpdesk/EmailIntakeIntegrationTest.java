package com.helpdesk;

import com.helpdesk.comment.CommentRepository;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.security.JwtService;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email intake end to end: the inbound key picks the tenant, registered customers are
 * linked automatically, other senders wait for an agent to link them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailIntakeIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
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

    private Organization orgA;
    private User adminA;
    private User agentA;
    private User customerA;
    private User customerB;

    @BeforeEach
    void seed() {
        historyRepository.deleteAll();
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        orgA = organizationRepository.save(new Organization("Org A", "org-a"));
        Organization orgB = organizationRepository.save(new Organization("Org B", "org-b"));
        adminA = userRepository.save(new User(orgA, "admin@a.test", "x", "Admin A", Role.ORG_ADMIN));
        agentA = userRepository.save(new User(orgA, "agent@a.test", "x", "Agent A", Role.AGENT));
        customerA = userRepository.save(new User(orgA, "riya@a.test", "x", "Riya", Role.CUSTOMER));
        customerB = userRepository.save(new User(orgB, "sam@b.test", "x", "Sam", Role.CUSTOMER));
    }

    private ResultActions mail(String key, String from, String thread, String messageId) throws Exception {
        String json = """
                {"threadId":"%s","messageId":"%s","fromEmail":"%s","fromName":"Sender",
                 "subject":"Help needed","body":"Something is broken"}
                """.formatted(thread, messageId, from);
        MockHttpServletRequestBuilder request = post("/api/inbound/email").contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("X-Inbound-Key", key);
        }
        return mvc.perform(request);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private long createdTicketId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.ticketId")).longValue();
    }

    @Test
    void missingOrWrongKeyIsRejected() throws Exception {
        mail(null, "riya@a.test", "t1", "m1").andExpect(status().isUnauthorized());
        mail("not-a-real-key", "riya@a.test", "t1", "m1").andExpect(status().isUnauthorized());
    }

    @Test
    void mailFromRegisteredCustomerIsLinkedAutomatically() throws Exception {
        long id = createdTicketId(mail(orgA.getInboundKey(), "RIYA@a.test", "t1", "m1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome", is("TICKET_CREATED")))
                .andExpect(jsonPath("$.linkedToCustomer", is(true))));

        mvc.perform(as(customerA, get("/api/tickets/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel", is("EMAIL")))
                .andExpect(jsonPath("$.requester.id", is(customerA.getId().intValue())));
    }

    @Test
    void mailFromUnknownAddressWaitsForLinkingThenAgentLinksIt() throws Exception {
        long id = createdTicketId(mail(orgA.getInboundKey(), "riya.personal@gmail.com", "t2", "m1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedToCustomer", is(false))));

        // unlinked: agents see it in the queue, the customer cannot see it yet
        mvc.perform(as(agentA, get("/api/tickets").param("unlinked", "true")))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].requester", nullValue()))
                .andExpect(jsonPath("$.content[0].senderEmail", is("riya.personal@gmail.com")));
        mvc.perform(as(customerA, get("/api/tickets/" + id))).andExpect(status().isNotFound());

        mvc.perform(as(agentA, put("/api/tickets/" + id + "/requester")).content("{\"requesterId\":" + customerA.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.id", is(customerA.getId().intValue())))
                .andExpect(jsonPath("$.senderEmail", is("riya.personal@gmail.com")));

        mvc.perform(as(customerA, get("/api/tickets/" + id))).andExpect(status().isOk());
        mvc.perform(as(agentA, get("/api/tickets/" + id + "/history")))
                .andExpect(jsonPath("$[1].type", is("REQUESTER_CHANGED")))
                .andExpect(jsonPath("$[1].fromValue", is("riya.personal@gmail.com")));
    }

    @Test
    void customerOfAnotherOrganizationIsAStrangerHere() throws Exception {
        // Sam is registered in org B, but mails org A's helpdesk: must not be linked to anyone
        mail(orgA.getInboundKey(), "sam@b.test", "t3", "m1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedToCustomer", is(false)));
    }

    @Test
    void ticketCannotBeLinkedToUserOfAnotherOrganizationOrByCustomer() throws Exception {
        long id = createdTicketId(mail(orgA.getInboundKey(), "someone@gmail.com", "t4", "m1"));

        mvc.perform(as(agentA, put("/api/tickets/" + id + "/requester")).content("{\"requesterId\":" + customerB.getId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(agentA, put("/api/tickets/" + id + "/requester")).content("{\"requesterId\":" + adminA.getId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(customerA, put("/api/tickets/" + id + "/requester")).content("{\"requesterId\":" + customerA.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void replyOnSameThreadBecomesCommentAndRetriesAreIgnored() throws Exception {
        long id = createdTicketId(mail(orgA.getInboundKey(), "riya@a.test", "t5", "m1"));

        mail(orgA.getInboundKey(), "riya@a.test", "t5", "m2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("COMMENT_ADDED")))
                .andExpect(jsonPath("$.ticketId", is((int) id)));
        mail(orgA.getInboundKey(), "riya@a.test", "t5", "m2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DUPLICATE")));

        mvc.perform(as(agentA, get("/api/tickets").param("q", "Help needed")))
                .andExpect(jsonPath("$.totalElements", is(1)));
        mvc.perform(as(agentA, get("/api/tickets/" + id + "/comments")))
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].viaEmail", is(true)));
    }

    @Test
    void sameThreadIdInAnotherOrganizationIsASeparateTicket() throws Exception {
        Organization orgB = organizationRepository.findAll().stream()
                .filter(o -> o.getSlug().equals("org-b")).findFirst().orElseThrow();
        mail(orgA.getInboundKey(), "riya@a.test", "shared-thread", "m1").andExpect(status().isCreated());
        mail(orgB.getInboundKey(), "sam@b.test", "shared-thread", "m1").andExpect(status().isCreated());
    }

    @Test
    void onlyAdminCanSeeAndRotateTheInboundKey() throws Exception {
        mvc.perform(as(agentA, get("/api/organization/email-intake"))).andExpect(status().isForbidden());
        String oldKey = orgA.getInboundKey();
        mvc.perform(as(adminA, post("/api/organization/email-intake/rotate")))
                .andExpect(status().isOk());
        mail(oldKey, "riya@a.test", "t6", "m1").andExpect(status().isUnauthorized());
    }
}
