package com.cimportal.common.error;

public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public ApiException(ErrorCode code, String message) { super(message); this.code = code; }

    public static ApiException notFound(String what)    { return new ApiException(ErrorCode.NOT_FOUND, what + " 不存在"); }
    public static ApiException duplicate(String message) { return new ApiException(ErrorCode.DUPLICATE_CODE, message); }
    public static ApiException inUse(String message)     { return new ApiException(ErrorCode.IN_USE, message); }
    public static ApiException badRequest(String message){ return new ApiException(ErrorCode.VALIDATION_FAILED, message); }
}
