package com.cimportal.link.dto;

import jakarta.validation.constraints.NotBlank;

public class LinkRequest {
    @NotBlank private String nameZh;
    @NotBlank private String nameEn;
    private String url;
    @NotBlank private String icon;
    @NotBlank private String categoryCode;
    @NotBlank private String statusCode;
    private int sortOrder;
    private Boolean openInNewTab;
    private String urlDev;
    private String urlUat;
    private String urlRelease;

    public LinkRequest() { }

    public String getNameZh() { return nameZh; }
    public String getNameEn() { return nameEn; }
    public String getUrl() { return url; }
    public String getIcon() { return icon; }
    public String getCategoryCode() { return categoryCode; }
    public String getStatusCode() { return statusCode; }
    public int getSortOrder() { return sortOrder; }
    public Boolean getOpenInNewTab() { return openInNewTab; }
    public String getUrlDev() { return urlDev; }
    public String getUrlUat() { return urlUat; }
    public String getUrlRelease() { return urlRelease; }

    public void setNameZh(String v) { this.nameZh = v; }
    public void setNameEn(String v) { this.nameEn = v; }
    public void setUrl(String v) { this.url = v; }
    public void setIcon(String v) { this.icon = v; }
    public void setCategoryCode(String v) { this.categoryCode = v; }
    public void setStatusCode(String v) { this.statusCode = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setOpenInNewTab(Boolean v) { this.openInNewTab = v; }
    public void setUrlDev(String v) { this.urlDev = v; }
    public void setUrlUat(String v) { this.urlUat = v; }
    public void setUrlRelease(String v) { this.urlRelease = v; }

    public boolean openInNewTabOrDefault() { return openInNewTab == null || openInNewTab; }

    @jakarta.validation.constraints.AssertTrue(message = "Provide a single URL, or all three of DEV/UAT/RELEASE URLs")
    public boolean isValidUrlConfig() {
        boolean envAll = nb(urlDev) && nb(urlUat) && nb(urlRelease);
        boolean envNone = !nb(urlDev) && !nb(urlUat) && !nb(urlRelease);
        boolean single = nb(url);
        return (single && envNone) || (!single && envAll);
    }

    private static boolean nb(String s) { return s != null && !s.isBlank(); }
}
