package com.helpdesk.comment;

import com.helpdesk.ticket.Ticket;
import com.helpdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A reply on a ticket. {@code internal = true} marks an agent note that customers never see.
 * A reply that arrived by mail from an unregistered address has no author, only the
 * sender's email and name.
 */
@Entity
@Table(name = "ticket_comments", indexes = {
        @Index(name = "idx_comments_ticket_created", columnList = "ticket_id, created_at"),
        @Index(name = "idx_comments_email_message", columnList = "email_message_id")
})
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "external_author_email", length = 160)
    private String externalAuthorEmail;

    @Column(name = "external_author_name", length = 120)
    private String externalAuthorName;

    @Column(name = "email_message_id", length = 250)
    private String emailMessageId;

    @Lob
    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(Ticket ticket, User author, String body, boolean internal) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
        this.internal = internal;
    }

    /** A mail reply on the ticket's thread; {@code author} is null for an unregistered sender. */
    public static Comment fromEmail(Ticket ticket, User author, String body, String senderEmail, String senderName,
                                    String messageId) {
        Comment comment = new Comment(ticket, author, body, false);
        comment.externalAuthorEmail = senderEmail;
        comment.externalAuthorName = senderName;
        comment.emailMessageId = messageId;
        return comment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAuthor() {
        return author;
    }

    public String getExternalAuthorEmail() {
        return externalAuthorEmail;
    }

    public String getExternalAuthorName() {
        return externalAuthorName;
    }

    public String getEmailMessageId() {
        return emailMessageId;
    }

    public String getBody() {
        return body;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
