package com.cimportal.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class ApiErrorResponder {
    private final ObjectMapper mapper;
    public ApiErrorResponder(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletRequest req, HttpServletResponse res, ErrorCode code, String message)
            throws IOException {
        res.setStatus(code.status.value());
        res.setContentType("application/json;charset=UTF-8");
        ApiError body = new ApiError(Instant.now(), code.status.value(),
            code.status.getReasonPhrase(), code.name(), message, req.getRequestURI(), null);
        mapper.writeValue(res.getWriter(), body);
    }
}
