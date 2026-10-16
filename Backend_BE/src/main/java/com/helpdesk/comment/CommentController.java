package com.helpdesk.comment;

import com.helpdesk.comment.CommentDtos.CommentResponse;
import com.helpdesk.comment.CommentDtos.CreateCommentRequest;
import com.helpdesk.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
@Tag(name = "Comments", description = "Replies and internal agent notes on a ticket")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    @Operation(summary = "Comments on a ticket (customers never receive internal notes)")
    public List<CommentResponse> list(@AuthenticationPrincipal AuthUser me, @PathVariable Long ticketId) {
        return commentService.list(me, ticketId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a reply, or an internal note with internal = true (admin, agent)")
    public CommentResponse add(@AuthenticationPrincipal AuthUser me, @PathVariable Long ticketId,
                               @Valid @RequestBody CreateCommentRequest request) {
        return commentService.add(me, ticketId, request);
    }
}
