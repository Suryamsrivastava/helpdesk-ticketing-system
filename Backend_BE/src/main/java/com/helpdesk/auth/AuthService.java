package com.helpdesk.auth;

import com.helpdesk.auth.AuthDtos.AuthResponse;
import com.helpdesk.auth.AuthDtos.LoginRequest;
import com.helpdesk.auth.AuthDtos.RegisterOrganizationRequest;
import com.helpdesk.common.exception.ApiException;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import com.helpdesk.security.AuthUser;
import com.helpdesk.security.JwtService;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import com.helpdesk.user.dto.UserDtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, OrganizationRepository organizationRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse registerOrganization(RegisterOrganizationRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Organization organization = organizationRepository.save(
                new Organization(request.organizationName().trim(), uniqueSlug(request.organizationName())));
        User admin = userRepository.save(new User(organization, email,
                passwordEncoder.encode(request.password()), request.adminName().trim(), Role.ORG_ADMIN));
        return AuthResponse.bearer(jwtService.generateToken(admin), jwtService.getTtlSeconds(), UserResponse.of(admin));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!user.isActive()) {
            throw ApiException.forbidden("This account has been deactivated");
        }
        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.getTtlSeconds(), UserResponse.of(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser me) {
        return userRepository.findWithOrganizationById(me.id())
                .map(UserResponse::of)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** "Acme Corp!" -> "acme-corp", then "acme-corp-2" if taken. */
    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "org";
        }
        if (base.length() > 70) {
            base = base.substring(0, 70);
        }
        String slug = base;
        int suffix = 2;
        while (organizationRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
