package com.helpdesk.config;

import com.helpdesk.comment.Comment;
import com.helpdesk.comment.CommentRepository;
import com.helpdesk.history.HistoryType;
import com.helpdesk.history.TicketHistory;
import com.helpdesk.history.TicketHistoryRepository;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketPriority;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketStatus;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two demo organizations so tenant isolation can be tried straight away.
 * Runs only on an empty database; disable with SEED_DEMO_DATA=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Password@123";

    /** Fixed so the README's curl examples work on a fresh database. Demo only. */
    public static final String ACME_INBOUND_KEY = "demo-acme-inbound-key-change-me";
    public static final String GLOBEX_INBOUND_KEY = "demo-globex-inbound-key-change-me";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(OrganizationRepository organizationRepository, UserRepository userRepository,
                          TicketRepository ticketRepository, CommentRepository commentRepository,
                          TicketHistoryRepository historyRepository, PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (organizationRepository.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        Organization acme = new Organization("Acme Corp", "acme-corp");
        acme.setInboundKey(ACME_INBOUND_KEY);
        acme = organizationRepository.save(acme);
        User acmeAdmin = user(acme, "admin@acme.test", "Alice Admin", Role.ORG_ADMIN, hash);
        User arjun = user(acme, "agent1@acme.test", "Arjun Agent", Role.AGENT, hash);
        User aisha = user(acme, "agent2@acme.test", "Aisha Agent", Role.AGENT, hash);
        User carlos = user(acme, "customer1@acme.test", "Carlos Customer", Role.CUSTOMER, hash);
        User chloe = user(acme, "customer2@acme.test", "Chloe Customer", Role.CUSTOMER, hash);

        Ticket login = ticket(acme, carlos, "Cannot log in to the mobile app",
                "Since this morning the app says 'session expired' right after I enter my password.",
                TicketPriority.HIGH);
        assign(login, arjun, acmeAdmin);
        comment(login, carlos, "This is blocking my whole team, please help.", false);
        comment(login, arjun, "Thanks Carlos, we are looking into it. Could you tell us your app version?", false);
        comment(login, arjun, "Looks like the SSO token expiry bug from last release. Checking with the mobile team.", true);

        ticket(acme, chloe, "Invoice shows the wrong GST amount",
                "The October invoice charges 18% GST on an exempt line item.", TicketPriority.MEDIUM);

        Ticket seat = ticket(acme, carlos, "Please add one more user seat",
                "We hired a new colleague and need one more seat on our plan.", TicketPriority.LOW);
        assign(seat, aisha, acmeAdmin);
        status(seat, TicketStatus.RESOLVED, aisha);
        comment(seat, aisha, "Done - the seat is added and an invite was sent.", false);

        Ticket outage = ticket(acme, chloe, "Website is down for EU customers",
                "Our EU storefront returns 502 errors since 09:10 CET.", TicketPriority.URGENT);
        assign(outage, arjun, acmeAdmin);
        status(outage, TicketStatus.RESOLVED, arjun);
        status(outage, TicketStatus.CLOSED, chloe);
        comment(outage, arjun, "Root cause: expired TLS certificate on the EU load balancer.", true);

        // email intake: one mail from a registered customer (linked automatically) and one from an
        // address nobody is registered with (waits in "Needs linking" for an agent)
        emailTicket(acme, chloe, "customer2@acme.test", "Chloe Customer",
                "Cannot download last month's invoice", "The download button on the billing page does nothing.",
                "demo-thread-acme-1");
        Ticket unknown = emailTicket(acme, null, "rohan.mehta.home@gmail.com", "Rohan Mehta",
                "Order delivered with a damaged item",
                "Hi, I am an Acme customer but writing from my personal mail. One chair arrived broken.",
                "demo-thread-acme-2");
        commentRepository.save(Comment.fromEmail(unknown, null, "Adding a photo link: https://example.com/chair.jpg",
                "rohan.mehta.home@gmail.com", "Rohan Mehta", "demo-message-acme-2b"));

        Organization globex = new Organization("Globex Inc", "globex-inc");
        globex.setInboundKey(GLOBEX_INBOUND_KEY);
        globex = organizationRepository.save(globex);
        User globexAdmin = user(globex, "admin@globex.test", "Gina Admin", Role.ORG_ADMIN, hash);
        User gabe = user(globex, "agent1@globex.test", "Gabe Agent", Role.AGENT, hash);
        User grace = user(globex, "customer1@globex.test", "Grace Customer", Role.CUSTOMER, hash);

        Ticket api = ticket(globex, grace, "API returns 429 too often",
                "Our integration hits rate limits at only 10 requests per minute.", TicketPriority.HIGH);
        assign(api, gabe, globexAdmin);
        comment(api, gabe, "Customer is on the free tier - limit is expected. Suggest upgrade.", true);
        ticket(globex, grace, "How do I export my data?", "Is there a CSV export for all tickets?", TicketPriority.LOW);

        log.info("Demo data created: 2 organizations, 8 users, 8 tickets (password for all: {})", DEMO_PASSWORD);
    }

    private Ticket emailTicket(Organization org, User requester, String fromEmail, String fromName,
                               String subject, String body, String threadId) {
        Ticket ticket = ticketRepository.save(Ticket.fromEmail(org, requester, subject, body,
                fromEmail, fromName, threadId, threadId + "-m1"));
        historyRepository.save(new TicketHistory(ticket, requester, HistoryType.CREATED, null, TicketStatus.OPEN.name()));
        return ticket;
    }

    private User user(Organization org, String email, String name, Role role, String hash) {
        return userRepository.save(new User(org, email, hash, name, role));
    }

    private Ticket ticket(Organization org, User requester, String subject, String description, TicketPriority priority) {
        Ticket ticket = ticketRepository.save(new Ticket(org, requester, subject, description, priority));
        historyRepository.save(new TicketHistory(ticket, requester, HistoryType.CREATED, null, TicketStatus.OPEN.name()));
        return ticket;
    }

    private void assign(Ticket ticket, User agent, User actor) {
        ticket.setAssignee(agent);
        historyRepository.save(new TicketHistory(ticket, actor, HistoryType.ASSIGNEE_CHANGED, null, agent.getFullName()));
        status(ticket, TicketStatus.IN_PROGRESS, actor);
    }

    private void status(Ticket ticket, TicketStatus to, User actor) {
        TicketStatus from = ticket.getStatus();
        ticket.changeStatus(to);
        historyRepository.save(new TicketHistory(ticket, actor, HistoryType.STATUS_CHANGED, from.name(), to.name()));
    }

    private void comment(Ticket ticket, User author, String body, boolean internal) {
        commentRepository.save(new Comment(ticket, author, body, internal));
    }
}
