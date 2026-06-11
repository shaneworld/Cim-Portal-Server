package com.cimportal.announcement;

import jakarta.persistence.*;

@Entity
@Table(name = "announcement_type")
public class AnnouncementType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "label_zh", nullable = false, length = 255)
    private String labelZh;

    @Column(name = "label_en", nullable = false, length = 255)
    private String labelEn;

    @Column(nullable = false, length = 16)
    private String color = "blue";

    @Column(nullable = false, length = 64)
    private String icon = "info";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    protected AnnouncementType() { }

    public AnnouncementType(String code, String labelZh, String labelEn,
                            String color, String icon, int sortOrder, boolean active) {
        this.code = code;
        this.labelZh = labelZh;
        this.labelEn = labelEn;
        this.color = color;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getLabelZh() { return labelZh; }
    public String getLabelEn() { return labelEn; }
    public String getColor() { return color; }
    public String getIcon() { return icon; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }

    public void setCode(String code) { this.code = code; }
    public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public void setColor(String color) { this.color = color; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setActive(boolean active) { this.active = active; }
}
