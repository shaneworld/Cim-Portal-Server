package com.cimportal.quicklink;

import jakarta.persistence.*;

@Entity
@Table(name = "quick_link")
public class QuickLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label_zh", nullable = false, length = 255)
    private String labelZh;

    @Column(name = "label_en", nullable = false, length = 255)
    private String labelEn;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(length = 64)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    protected QuickLink() { }

    public QuickLink(String labelZh, String labelEn, String url, String icon, int sortOrder, boolean active) {
        this.labelZh = labelZh;
        this.labelEn = labelEn;
        this.url = url;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getLabelZh() { return labelZh; }
    public String getLabelEn() { return labelEn; }
    public String getUrl() { return url; }
    public String getIcon() { return icon; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }

    public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public void setUrl(String url) { this.url = url; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setActive(boolean active) { this.active = active; }
}
