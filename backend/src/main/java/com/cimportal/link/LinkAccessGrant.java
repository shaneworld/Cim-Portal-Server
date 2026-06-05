package com.cimportal.link;

import jakarta.persistence.*;

@Entity
@Table(name = "link_access_grant",
       uniqueConstraints = @UniqueConstraint(name = "uk_grant",
           columnNames = {"link_id", "grant_type", "grant_code"}))
public class LinkAccessGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "link_id", nullable = false) private Long linkId;
    @Enumerated(EnumType.STRING) @Column(name = "grant_type", nullable = false, length = 16)
    private GrantType grantType;
    @Column(name = "grant_code", nullable = false, length = 64) private String grantCode;

    protected LinkAccessGrant() { }
    public LinkAccessGrant(Long linkId, GrantType grantType, String grantCode) {
        this.linkId = linkId; this.grantType = grantType; this.grantCode = grantCode;
    }
    public Long getId() { return id; }
    public Long getLinkId() { return linkId; }
    public GrantType getGrantType() { return grantType; }
    public String getGrantCode() { return grantCode; }
}
