package com.helpdesk.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every lookup except login is scoped by organization id.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "organization")
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "organization")
    Optional<User> findWithOrganizationById(Long id);

    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

    Page<User> findByOrganizationId(Long organizationId, Pageable pageable);

    Page<User> findByOrganizationIdAndRole(Long organizationId, Role role, Pageable pageable);

    List<User> findByOrganizationIdAndRoleAndActiveTrueOrderByFullNameAsc(Long organizationId, Role role);
}
