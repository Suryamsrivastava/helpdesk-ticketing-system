package com.helpdesk.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** The only way the service loads a single ticket: by id AND tenant. */
    @EntityGraph(attributePaths = {"requester", "assignee"})
    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    @Override
    @EntityGraph(attributePaths = {"requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    Optional<Ticket> findByOrganizationIdAndEmailThreadId(Long organizationId, String emailThreadId);

    boolean existsByOrganizationIdAndEmailMessageId(Long organizationId, String emailMessageId);
}
