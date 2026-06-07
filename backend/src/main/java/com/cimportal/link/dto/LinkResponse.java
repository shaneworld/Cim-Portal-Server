package com.cimportal.link.dto;

import com.cimportal.link.Link;
import java.time.Instant;
import java.util.List;

public record LinkResponse(
    Long id, String nameZh, String nameEn,
    String url, String urlDev, String urlUat, String urlRelease,
    String icon, String categoryCode, String statusCode, int sortOrder, boolean openInNewTab,
    List<GrantResponse> grants, Instant createdAt, Instant updatedAt
) {
    public static LinkResponse of(Link l, List<GrantResponse> grants) {
        return new LinkResponse(l.getId(), l.getNameZh(), l.getNameEn(),
            l.getUrl(), l.getUrlDev(), l.getUrlUat(), l.getUrlRelease(),
            l.getIcon(), l.getCategoryCode(), l.getStatusCode(), l.getSortOrder(),
            l.isOpenInNewTab(), grants, l.getCreatedAt(), l.getUpdatedAt());
    }
}
