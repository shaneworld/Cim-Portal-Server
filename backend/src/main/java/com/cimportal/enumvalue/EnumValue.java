package com.cimportal.enumvalue;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "enum_value",
       uniqueConstraints = @UniqueConstraint(name = "uk_enum_category_code",
                                             columnNames = {"category", "code"}))
public class EnumValue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EnumCategory category;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "label_zh", nullable = false, length = 255) private String labelZh;
    @Column(name = "label_en", nullable = false, length = 255) private String labelEn;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;

    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp  @Column(name = "updated_at") private Instant updatedAt;

    protected EnumValue() { }

    public EnumValue(EnumCategory category, String code, String labelZh, String labelEn,
                     int sortOrder, boolean active) {
        this.category = category; this.code = code; this.labelZh = labelZh;
        this.labelEn = labelEn; this.sortOrder = sortOrder; this.active = active;
    }

    public Long getId() { return id; }
    public EnumCategory getCategory() { return category; }
    public String getCode() { return code; }
    public String getLabelZh() { return labelZh; }
    public String getLabelEn() { return labelEn; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setLabelZh(String v) { this.labelZh = v; }
    public void setLabelEn(String v) { this.labelEn = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setActive(boolean v) { this.active = v; }
}
