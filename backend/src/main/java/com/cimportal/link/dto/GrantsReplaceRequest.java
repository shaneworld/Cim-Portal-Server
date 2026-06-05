package com.cimportal.link.dto;

import jakarta.validation.Valid;
import java.util.List;

public record GrantsReplaceRequest(@Valid List<GrantRequest> grants) {
    public List<GrantRequest> safeGrants() { return grants == null ? List.of() : grants; }
}
