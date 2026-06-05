package com.cimportal.auth;

import com.cimportal.common.error.ApiErrorResponder;
import com.cimportal.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Emits the ApiError JSON body for filter-chain 403s. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final ApiErrorResponder responder;
    public RestAccessDeniedHandler(ApiErrorResponder responder) { this.responder = responder; }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        responder.write(req, res, ErrorCode.FORBIDDEN, "无权访问");
    }
}
