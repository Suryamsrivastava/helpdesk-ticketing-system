package com.helpdesk.auth;

import com.helpdesk.user.dto.UserDtos.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    /** Creates a new tenant and its first Org Admin in one step. */
    public record RegisterOrganizationRequest(
            @NotBlank @Size(max = 120) String organizationName,
            @NotBlank @Size(max = 120) String adminName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {

        public static AuthResponse bearer(String token, long expiresIn, UserResponse user) {
            return new AuthResponse(token, "Bearer", expiresIn, user);
        }
    }
}
