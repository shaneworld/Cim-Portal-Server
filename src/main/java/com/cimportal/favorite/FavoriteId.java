package com.cimportal.favorite;

import java.io.Serializable;
import java.util.Objects;

public class FavoriteId implements Serializable {
    private String employeeId;
    private Long linkId;

    public FavoriteId() { }
    public FavoriteId(String employeeId, Long linkId) {
        this.employeeId = employeeId;
        this.linkId = linkId;
    }

    public String getEmployeeId() { return employeeId; }
    public Long getLinkId() { return linkId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoriteId that)) return false;
        return Objects.equals(employeeId, that.employeeId) && Objects.equals(linkId, that.linkId);
    }
    @Override public int hashCode() { return Objects.hash(employeeId, linkId); }
}
