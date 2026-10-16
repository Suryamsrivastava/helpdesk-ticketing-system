package com.helpdesk.ticket;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} allowed = {2}")
    @CsvSource({
            "OPEN, IN_PROGRESS, true",
            "OPEN, CLOSED, true",
            "IN_PROGRESS, RESOLVED, true",
            "RESOLVED, CLOSED, true",
            "RESOLVED, OPEN, true",
            "CLOSED, OPEN, true",
            "CLOSED, RESOLVED, false",
            "CLOSED, IN_PROGRESS, false"
    })
    void transitions(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(TicketStatusPolicy.canTransition(from, to)).isEqualTo(allowed);
    }

    @ParameterizedTest(name = "customer {0} -> {1} allowed = {2}")
    @CsvSource({
            "OPEN, CLOSED, true",
            "RESOLVED, OPEN, true",
            "CLOSED, OPEN, true",
            "OPEN, RESOLVED, false",
            "OPEN, IN_PROGRESS, false",
            "IN_PROGRESS, OPEN, false"
    })
    void customerMoves(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(TicketStatusPolicy.customerMay(from, to)).isEqualTo(allowed);
    }
}
