package com.cimportal.label;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "label", uniqueConstraints = @UniqueConstraint(name = "uk_label_key", columnNames = "label_key"))
public class Label {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "label_key", nullable = false, length = 128) private String labelKey;
    @Column(nullable = false, length = 32) private String type;
    @Column(name = "text_zh", nullable = false, length = 1024) private String textZh;
    @Column(name = "text_en", nullable = false, length = 1024) private String textEn;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;

    protected Label() { }
    public Label(String labelKey, String type, String textZh, String textEn) {
        this.labelKey = labelKey; this.type = type; this.textZh = textZh; this.textEn = textEn;
    }
    public Long getId() { return id; }
    public String getLabelKey() { return labelKey; }
    public String getType() { return type; }
    public String getTextZh() { return textZh; }
    public String getTextEn() { return textEn; }
    public void setType(String v) { this.type = v; }
    public void setTextZh(String v) { this.textZh = v; }
    public void setTextEn(String v) { this.textEn = v; }
}
