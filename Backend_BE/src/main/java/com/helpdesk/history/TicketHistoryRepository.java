package com.helpdesk.history;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    @EntityGraph(attributePaths = "actor")
    List<TicketHistory> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
