package com.cimportal.favorite;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "favorite")
@IdClass(FavoriteId.class)
public class Favorite {

    @Id @Column(name = "employee_id", length = 64) private String employeeId;
    @Id @Column(name = "link_id") private Long linkId;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;

    protected Favorite() { }

    public Favorite(String employeeId, Long linkId) {
        this.employeeId = employeeId;
        this.linkId = linkId;
    }

    public String getEmployeeId() { return employeeId; }
    public Long getLinkId() { return linkId; }
    public Instant getCreatedAt() { return createdAt; }
}
