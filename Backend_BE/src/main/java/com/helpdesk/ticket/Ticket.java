package com.helpdesk.ticket;

import com.helpdesk.organization.Organization;
import com.helpdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_tickets_org_thread", columnNames = {"organization_id", "email_thread_id"}),
        indexes = {
        @Index(name = "idx_tickets_org_status", columnList = "organization_id, status"),
        @Index(name = "idx_tickets_org_priority", columnList = "organization_id, priority"),
        @Index(name = "idx_tickets_org_assignee", columnList = "organization_id, assignee_id"),
        @Index(name = "idx_tickets_org_requester", columnList = "organization_id, requester_id"),
        @Index(name = "idx_tickets_org_created", columnList = "organization_id, created_at")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String subject;

    // TEXT, not @Lob: on MySQL a @Lob String becomes TINYTEXT (255 bytes)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    /** Null only for an email ticket whose sender is not a registered user yet ("needs linking"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketChannel channel = TicketChannel.WEB;

    /** For email tickets: the address the mail came from, kept even after linking. */
    @Column(name = "sender_email", length = 160)
    private String senderEmail;

    @Column(name = "sender_name", length = 120)
    private String senderName;

    /** Mail thread id; replies on the same thread become comments, not new tickets. */
    @Column(name = "email_thread_id", length = 200)
    private String emailThreadId;

    @Column(name = "email_message_id", length = 250)
    private String emailMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Optimistic lock: two agents saving the same ticket at once get a 409, not a silent overwrite. */
    @Version
    private Long version;

    protected Ticket() {
    }

    public Ticket(Organization organization, User requester, String subject, String description, TicketPriority priority) {
        this.organization = organization;
        this.requester = requester;
        this.subject = subject;
        this.description = description;
        this.priority = priority;
    }

    /** A ticket raised by an inbound mail; {@code requester} is null when the sender is unknown. */
    public static Ticket fromEmail(Organization organization, User requester, String subject, String description,
                                   String senderEmail, String senderName, String threadId, String messageId) {
        Ticket ticket = new Ticket(organization, requester, subject, description, TicketPriority.MEDIUM);
        ticket.channel = TicketChannel.EMAIL;
        ticket.senderEmail = senderEmail;
        ticket.senderName = senderName;
        ticket.emailThreadId = threadId;
        ticket.emailMessageId = messageId;
        return ticket;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Moves the ticket and keeps the resolved/closed stamps consistent with it.
     * Transition rules are checked by {@link TicketStatusPolicy} before this is called.
     */
    public void changeStatus(TicketStatus newStatus) {
        Instant now = Instant.now();
        switch (newStatus) {
            case RESOLVED -> {
                resolvedAt = now;
                closedAt = null;
            }
            case CLOSED -> {
                if (resolvedAt == null) {
                    resolvedAt = now;
                }
                closedAt = now;
            }
            default -> {
                resolvedAt = null;
                closedAt = null;
            }
        }
        this.status = newStatus;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getRequester() {
        return requester;
    }

    public void setRequester(User requester) {
        this.requester = requester;
    }

    public TicketChannel getChannel() {
        return channel == null ? TicketChannel.WEB : channel;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getEmailThreadId() {
        return emailThreadId;
    }

    public String getEmailMessageId() {
        return emailMessageId;
    }

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }
}
