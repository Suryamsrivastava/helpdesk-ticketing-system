package com.helpdesk.user;

import com.helpdesk.common.PageResponse;
import com.helpdesk.security.AuthUser;
import com.helpdesk.user.dto.UserDtos.CreateUserRequest;
import com.helpdesk.user.dto.UserDtos.UpdateUserRequest;
import com.helpdesk.user.dto.UserDtos.UserRef;
import com.helpdesk.user.dto.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Users of the caller's organization")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "List users of my organization (admin, agent)")
    public PageResponse<UserResponse> list(@AuthenticationPrincipal AuthUser me,
                                           @RequestParam(required = false) Role role,
                                           @ParameterObject @PageableDefault(sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(me, role, pageable);
    }

    @GetMapping("/agents")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "Active agents of my organization, for assignment (admin, agent)")
    public List<UserRef> agents(@AuthenticationPrincipal AuthUser me) {
        return userService.activeAgents(me);
    }

    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @Operation(summary = "Create an admin, agent or customer in my organization (admin)")
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal AuthUser me,
                                               @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(me, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @Operation(summary = "Rename, change role, activate or deactivate a user (admin)")
    public UserResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(me, id, request);
    }
}
