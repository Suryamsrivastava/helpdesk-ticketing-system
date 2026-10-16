package com.helpdesk.auth;

import com.helpdesk.auth.AuthDtos.AuthResponse;
import com.helpdesk.auth.AuthDtos.LoginRequest;
import com.helpdesk.auth.AuthDtos.RegisterOrganizationRequest;
import com.helpdesk.security.AuthUser;
import com.helpdesk.user.dto.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Sign up an organization, log in, current user")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-organization")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Create a new organization and its first Org Admin; returns a token")
    public AuthResponse registerOrganization(@Valid @RequestBody RegisterOrganizationRequest request) {
        return authService.registerOrganization(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in with email and password; returns a Bearer token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "The logged-in user and their organization")
    public UserResponse me(@AuthenticationPrincipal AuthUser me) {
        return authService.me(me);
    }
}
