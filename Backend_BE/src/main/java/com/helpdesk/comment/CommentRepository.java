package com.helpdesk.comment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    List<Comment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    /** What a customer sees: internal notes are filtered out in the query itself. */
    @EntityGraph(attributePaths = "author")
    List<Comment> findByTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(Long ticketId);

    boolean existsByTicket_Organization_IdAndEmailMessageId(Long organizationId, String emailMessageId);
}
