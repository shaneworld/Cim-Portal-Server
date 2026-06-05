package com.cimportal.label.dto;

import com.cimportal.label.Label;

public record LabelResponse(Long id, String labelKey, String type, String textZh, String textEn) {
    public static LabelResponse of(Label l) {
        return new LabelResponse(l.getId(), l.getLabelKey(), l.getType(), l.getTextZh(), l.getTextEn());
    }
}
