package com.cimportal.enumvalue;

/**
 * Inlined LINK_ENV presentation data resolved from the enum_value table.
 * All fields are null when the link has no environment set.
 */
public record EnvBadge(String envColor, String envLabelZh, String envLabelEn) {

    public static final EnvBadge EMPTY = new EnvBadge(null, null, null);

    /** Resolves color + labels for a LINK_ENV code, falling back to "slate"/code when missing. */
    public static EnvBadge resolve(EnumValueRepository repo, String code) {
        if (code == null || code.isBlank()) return EMPTY;
        EnumValue ev = repo.findByCategoryAndCode(EnumCategory.LINK_ENV, code).orElse(null);
        String color   = ev != null && ev.getColor() != null ? ev.getColor() : "slate";
        String labelZh = ev != null ? ev.getLabelZh() : code;
        String labelEn = ev != null ? ev.getLabelEn() : code;
        return new EnvBadge(color, labelZh, labelEn);
    }
}
