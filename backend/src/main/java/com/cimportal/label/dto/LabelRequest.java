package com.cimportal.label.dto;

import jakarta.validation.constraints.NotBlank;

public record LabelRequest(@NotBlank String labelKey, @NotBlank String type,
                           @NotBlank String textZh, @NotBlank String textEn) { }
