package com.cimportal.announcement;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "announcement")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title_zh", nullable = false, length = 255)
    private String titleZh;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Lob
    @Column(name = "body_zh", nullable = false)
    private String bodyZh;

    @Lob
    @Column(name = "body_en", nullable = false)
    private String bodyEn;

    @Column(name = "type_code", nullable = false, length = 64)
    private String typeCode;

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected Announcement() { }

    public Announcement(String titleZh, String titleEn, String bodyZh, String bodyEn,
                        String typeCode, boolean pinned, Instant startsAt, Instant endsAt,
                        boolean active) {
        this.titleZh = titleZh;
        this.titleEn = titleEn;
        this.bodyZh = bodyZh;
        this.bodyEn = bodyEn;
        this.typeCode = typeCode;
        this.pinned = pinned;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getTitleZh() { return titleZh; }
    public String getTitleEn() { return titleEn; }
    public String getBodyZh() { return bodyZh; }
    public String getBodyEn() { return bodyEn; }
    public String getTypeCode() { return typeCode; }
    public boolean isPinned() { return pinned; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public void setBodyZh(String bodyZh) { this.bodyZh = bodyZh; }
    public void setBodyEn(String bodyEn) { this.bodyEn = bodyEn; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setActive(boolean active) { this.active = active; }
}
