package com.cimportal.dutyline;

import jakarta.persistence.*;

@Entity
@Table(name = "duty_line")
public class DutyLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label_zh", nullable = false, length = 255)
    private String labelZh;

    @Column(name = "label_en", nullable = false, length = 255)
    private String labelEn;

    @Column(nullable = false, length = 64)
    private String phone;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    protected DutyLine() { }

    public DutyLine(String labelZh, String labelEn, String phone, int sortOrder, boolean active) {
        this.labelZh = labelZh;
        this.labelEn = labelEn;
        this.phone = phone;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getLabelZh() { return labelZh; }
    public String getLabelEn() { return labelEn; }
    public String getPhone() { return phone; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }

    public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setActive(boolean active) { this.active = active; }
}
