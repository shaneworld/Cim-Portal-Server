package com.cimportal.link;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "link")
public class Link {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "name_zh", nullable = false, length = 255) private String nameZh;
    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(nullable = false, length = 1024) private String url;
    @Column(nullable = false, length = 64) private String icon;
    @Column(name = "category_code", nullable = false, length = 64) private String categoryCode;
    @Column(name = "status_code", nullable = false, length = 64) private String statusCode;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "open_in_new_tab", nullable = false) private boolean openInNewTab = true;
    @Column(name = "environment", length = 16) private String environment;
    @Column(name = "launch_app", nullable = false) private boolean launchApp = false;
    @Column(name = "download_url", length = 1024) private String downloadUrl;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;

    protected Link() { }

    public Link(String nameZh, String nameEn, String url, String icon,
                String categoryCode, String statusCode, int sortOrder, boolean openInNewTab) {
        this.nameZh = nameZh; this.nameEn = nameEn; this.url = url;
        this.icon = icon; this.categoryCode = categoryCode; this.statusCode = statusCode;
        this.sortOrder = sortOrder; this.openInNewTab = openInNewTab;
    }

    public Long getId() { return id; }
    public String getNameZh() { return nameZh; }
    public String getNameEn() { return nameEn; }
    public String getUrl() { return url; }
    public String getIcon() { return icon; }
    public String getCategoryCode() { return categoryCode; }
    public String getStatusCode() { return statusCode; }
    public int getSortOrder() { return sortOrder; }
    public boolean isOpenInNewTab() { return openInNewTab; }
    public String getEnvironment() { return environment; }
    public boolean isLaunchApp() { return launchApp; }
    public String getDownloadUrl() { return downloadUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setNameZh(String v) { this.nameZh = v; }
    public void setNameEn(String v) { this.nameEn = v; }
    public void setUrl(String v) { this.url = v; }
    public void setIcon(String v) { this.icon = v; }
    public void setCategoryCode(String v) { this.categoryCode = v; }
    public void setStatusCode(String v) { this.statusCode = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setOpenInNewTab(boolean v) { this.openInNewTab = v; }
    public void setEnvironment(String v) { this.environment = v; }
    public void setLaunchApp(boolean v) { this.launchApp = v; }
    public void setDownloadUrl(String v) { this.downloadUrl = v; }
}
