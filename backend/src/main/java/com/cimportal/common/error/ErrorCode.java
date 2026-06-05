package com.cimportal.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_PROVISIONED(HttpStatus.NOT_FOUND),
    USER_INACTIVE(HttpStatus.FORBIDDEN),
    DUPLICATE_CODE(HttpStatus.CONFLICT),
    IN_USE(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
}
