package com.cimportal.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank String nameZh,
    @NotBlank String nameEn,
    Boolean active
) { }
