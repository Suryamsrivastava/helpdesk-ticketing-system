package com.helpdesk.user.dto;

import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    /** Short form used inside tickets, comments and history. */
    public record UserRef(Long id, String fullName, String email, Role role) {

        public static UserRef of(User user) {
            return user == null ? null : new UserRef(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        }
    }

    public record UserResponse(
            Long id,
            String fullName,
            String email,
            Role role,
            boolean active,
            Long organizationId,
            String organizationName,
            Instant createdAt
    ) {

        public static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive(),
                    user.getOrganization().getId(), user.getOrganization().getName(), user.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role
    ) {
    }

    /** Any field left null is unchanged. */
    public record UpdateUserRequest(
            @Size(max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String fullName,
            Role role,
            Boolean active
    ) {
    }
}
