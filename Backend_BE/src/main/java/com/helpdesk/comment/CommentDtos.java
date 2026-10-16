package com.helpdesk.comment;

import com.helpdesk.user.dto.UserDtos.UserRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CommentDtos {

    private CommentDtos() {
    }

    /** {@code internal = true} makes an agent note; only staff may send it. */
    public record CreateCommentRequest(
            @NotBlank @Size(max = 5000) String body,
            boolean internal
    ) {
    }

    /**
     * {@code author} is null for a mail reply from an unregistered address; the
     * {@code external*} fields then name the sender. {@code viaEmail} marks mail replies.
     */
    public record CommentResponse(Long id, String body, boolean internal, UserRef author,
                                  String externalAuthorName, String externalAuthorEmail,
                                  boolean viaEmail, Instant createdAt) {

        public static CommentResponse of(Comment c) {
            return new CommentResponse(c.getId(), c.getBody(), c.isInternal(), UserRef.of(c.getAuthor()),
                    c.getExternalAuthorName(), c.getExternalAuthorEmail(), c.getEmailMessageId() != null,
                    c.getCreatedAt());
        }
    }
}
