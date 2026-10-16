package com.helpdesk.inbound;

import com.helpdesk.inbound.InboundEmailDtos.InboundEmailRequest;
import com.helpdesk.inbound.InboundEmailDtos.InboundEmailResult;
import com.helpdesk.inbound.InboundEmailDtos.Outcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inbound")
@Tag(name = "Email intake", description = "Called by the helpdesk mailbox script, authenticated by the organization's inbound key")
public class InboundEmailController {

    public static final String KEY_HEADER = "X-Inbound-Key";

    private final InboundEmailService inboundEmailService;

    public InboundEmailController(InboundEmailService inboundEmailService) {
        this.inboundEmailService = inboundEmailService;
    }

    @PostMapping("/email")
    @SecurityRequirements
    @Operation(summary = "Turn an inbound mail into a ticket, or a comment when its thread already has one",
            description = "201 = ticket created, 200 = comment added or duplicate message, 401 = bad key")
    public ResponseEntity<InboundEmailResult> receive(
            @Parameter(description = "The organization's inbound key (Email intake page)")
            @RequestHeader(value = KEY_HEADER, required = false) String inboundKey,
            @Valid @RequestBody InboundEmailRequest request) {
        InboundEmailResult result = inboundEmailService.receive(inboundKey, request);
        HttpStatus status = result.outcome() == Outcome.TICKET_CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
