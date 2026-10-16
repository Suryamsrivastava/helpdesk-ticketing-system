package com.helpdesk.security;

import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import org.junit.jupiter.api.Test;

import static com.helpdesk.support.TestData.org;
import static com.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghij";

    private final User agent = user(org(7), 42, Role.AGENT);

    @Test
    void tokenRoundTripCarriesUserAndOrganization() {
        JwtService jwt = new JwtService(SECRET, 60);

        var claims = jwt.parse(jwt.generateToken(agent));

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(42L);
        assertThat(claims.get().organizationId()).isEqualTo(7L);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 60);
        String token = jwt.generateToken(agent);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtService("another-secret-0123456789-abcdefghijkl", 60).generateToken(agent);

        assertThat(new JwtService(SECRET, 60).parse(foreign)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1);

        assertThat(jwt.parse(jwt.generateToken(agent))).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("too-short", 60)).isInstanceOf(IllegalStateException.class);
    }
}
