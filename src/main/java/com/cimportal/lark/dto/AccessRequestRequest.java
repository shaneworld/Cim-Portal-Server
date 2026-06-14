package com.cimportal.lark.dto;

import jakarta.validation.constraints.NotNull;

public record AccessRequestRequest(@NotNull Long linkId, String reason) { }
