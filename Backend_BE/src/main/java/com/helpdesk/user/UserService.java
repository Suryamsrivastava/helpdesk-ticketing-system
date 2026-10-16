package com.helpdesk.user;

import com.helpdesk.common.PageResponse;
import com.helpdesk.common.SortGuard;
import com.helpdesk.common.exception.ApiException;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.security.AuthUser;
import com.helpdesk.user.dto.UserDtos.CreateUserRequest;
import com.helpdesk.user.dto.UserDtos.UpdateUserRequest;
import com.helpdesk.user.dto.UserDtos.UserRef;
import com.helpdesk.user.dto.UserDtos.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * User management inside the caller's own organization. The organization id always
 * comes from {@link AuthUser}, so an admin can never list or edit another tenant's users.
 */
@Service
public class UserService {

    private static final Set<String> USER_SORTS = Set.of("fullName", "email", "role", "createdAt", "id");

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, OrganizationRepository organizationRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(AuthUser me, Role role, Pageable pageable) {
        requireStaff(me);
        SortGuard.check(pageable, USER_SORTS);
        Page<User> page = role == null
                ? userRepository.findByOrganizationId(me.organizationId(), pageable)
                : userRepository.findByOrganizationIdAndRole(me.organizationId(), role, pageable);
        return PageResponse.from(page, UserResponse::of);
    }

    /** Active agents of the caller's organization, for the assignee picker. */
    @Transactional(readOnly = true)
    public List<UserRef> activeAgents(AuthUser me) {
        requireStaff(me);
        return userRepository.findByOrganizationIdAndRoleAndActiveTrueOrderByFullNameAsc(me.organizationId(), Role.AGENT)
                .stream().map(UserRef::of).toList();
    }

    @Transactional
    public UserResponse create(AuthUser me, CreateUserRequest request) {
        requireAdmin(me);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User(organizationRepository.getReferenceById(me.organizationId()), email,
                passwordEncoder.encode(request.password()), request.fullName().trim(), request.role());
        return UserResponse.of(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(AuthUser me, Long userId, UpdateUserRequest request) {
        requireAdmin(me);
        User user = userRepository.findByIdAndOrganizationId(userId, me.organizationId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        boolean self = user.getId().equals(me.id());
        if (self && request.role() != null && request.role() != user.getRole()) {
            throw ApiException.badRequest("You cannot change your own role");
        }
        if (self && Boolean.FALSE.equals(request.active())) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }

        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        return UserResponse.of(user);
    }

    private static void requireStaff(AuthUser me) {
        if (!me.isStaff()) {
            throw ApiException.forbidden("Only organization staff can view users");
        }
    }

    private static void requireAdmin(AuthUser me) {
        if (!me.isAdmin()) {
            throw ApiException.forbidden("Only an organization admin can manage users");
        }
    }
}
