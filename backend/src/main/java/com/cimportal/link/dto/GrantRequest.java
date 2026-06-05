package com.cimportal.link.dto;

import com.cimportal.link.GrantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GrantRequest(@NotNull GrantType grantType, @NotBlank String grantCode) { }
