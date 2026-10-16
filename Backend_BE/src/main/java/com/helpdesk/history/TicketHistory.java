package com.helpdesk.history;

import com.helpdesk.ticket.Ticket;
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
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit row: who changed what on a ticket, from which value to which.
 */
@Entity
@Table(name = "ticket_history",
        indexes = @Index(name = "idx_history_ticket_created", columnList = "ticket_id, created_at"))
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Null when the change came from an inbound mail by an unregistered sender. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HistoryType type;

    @Column(name = "from_value", length = 160)
    private String fromValue;

    @Column(name = "to_value", length = 160)
    private String toValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketHistory() {
    }

    public TicketHistory(Ticket ticket, User actor, HistoryType type, String fromValue, String toValue) {
        this.ticket = ticket;
        this.actor = actor;
        this.type = type;
        this.fromValue = fromValue;
        this.toValue = toValue;
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

    public User getActor() {
        return actor;
    }

    public HistoryType getType() {
        return type;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
