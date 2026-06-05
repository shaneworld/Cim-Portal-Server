package com.cimportal.auth;

import com.cimportal.common.error.ApiErrorResponder;
import com.cimportal.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Emits the ApiError JSON body for filter-chain 401s. */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {
    private final ApiErrorResponder responder;
    public RestAuthEntryPoint(ApiErrorResponder responder) { this.responder = responder; }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        responder.write(req, res, ErrorCode.UNAUTHENTICATED, "未认证");
    }
}
