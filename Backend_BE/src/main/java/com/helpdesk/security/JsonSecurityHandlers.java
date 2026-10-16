package com.helpdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 / 403 raised by the security filter chain come back in the same
 * {@link ApiError} shape as every other error.
 */
@Component
public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required: send a valid Bearer token", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message, HttpServletRequest request)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
