package com.cimportal.link.dto;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;

public record GrantResponse(Long id, Long linkId, GrantType grantType, String grantCode) {
    public static GrantResponse of(LinkAccessGrant g) {
        return new GrantResponse(g.getId(), g.getLinkId(), g.getGrantType(), g.getGrantCode());
    }
}
