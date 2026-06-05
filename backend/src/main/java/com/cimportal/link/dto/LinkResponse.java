package com.cimportal.link.dto;

import com.cimportal.link.Link;
import java.time.Instant;
import java.util.List;

public record LinkResponse(
    Long id, String code, String nameZh, String nameEn, String url, String icon,
    String categoryCode, String statusCode, int sortOrder, boolean openInNewTab,
    List<GrantResponse> grants, Instant createdAt, Instant updatedAt
) {
    public static LinkResponse of(Link l, List<GrantResponse> grants) {
        return new LinkResponse(l.getId(), l.getCode(), l.getNameZh(), l.getNameEn(), l.getUrl(),
            l.getIcon(), l.getCategoryCode(), l.getStatusCode(), l.getSortOrder(),
            l.isOpenInNewTab(), grants, l.getCreatedAt(), l.getUpdatedAt());
    }
}
