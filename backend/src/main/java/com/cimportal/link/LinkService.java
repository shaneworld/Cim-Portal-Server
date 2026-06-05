package com.cimportal.link;

import com.cimportal.common.error.ApiException;
import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.dto.GrantRequest;
import com.cimportal.link.dto.LinkRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LinkService {
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final EnumValueRepository enums;

    public LinkService(LinkRepository links, LinkAccessGrantRepository grants, EnumValueRepository enums) {
        this.links = links; this.grants = grants; this.enums = enums;
    }

    @Transactional(readOnly = true)
    public List<Link> listAll() { return links.findAllByOrderBySortOrderAscIdAsc(); }

    @Transactional(readOnly = true)
    public List<Link> search(String categoryCode, String statusCode, String q) {
        return links.search(emptyToNull(categoryCode), emptyToNull(statusCode), emptyToNull(q));
    }

    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    @Transactional(readOnly = true)
    public Link get(Long id) { return links.findById(id).orElseThrow(() -> ApiException.notFound("链接")); }

    @Transactional(readOnly = true)
    public List<LinkAccessGrant> grantsOf(Long linkId) {
        get(linkId);
        return grants.findByLinkId(linkId);
    }

    @Transactional
    public Link create(LinkRequest req) {
        if (links.existsByCode(req.code()))
            throw ApiException.duplicate("链接 code '" + req.code() + "' 已存在");
        requireEnum(EnumCategory.LINK_CATEGORY, req.categoryCode());
        requireEnum(EnumCategory.LINK_STATUS, req.statusCode());
        Link l = new Link();
        apply(l, req);
        return links.save(l);
    }

    @Transactional
    public Link update(Long id, LinkRequest req) {
        Link l = get(id);
        if (!l.getCode().equals(req.code()) && links.existsByCode(req.code()))
            throw ApiException.duplicate("链接 code '" + req.code() + "' 已存在");
        requireEnum(EnumCategory.LINK_CATEGORY, req.categoryCode());
        requireEnum(EnumCategory.LINK_STATUS, req.statusCode());
        apply(l, req);
        return l;
    }

    @Transactional
    public void delete(Long id) { links.delete(get(id)); }

    @Transactional
    public List<LinkAccessGrant> replaceGrants(Long linkId, List<GrantRequest> reqs) {
        get(linkId);
        for (GrantRequest g : reqs) requireGrantCode(g);
        grants.deleteByLinkId(linkId);
        grants.flush();
        for (GrantRequest g : reqs)
            grants.save(new LinkAccessGrant(linkId, g.grantType(), g.grantCode()));
        return grants.findByLinkId(linkId);
    }

    @Transactional
    public LinkAccessGrant addGrant(Long linkId, GrantRequest req) {
        get(linkId);
        requireGrantCode(req);
        boolean dup = grants.findByLinkId(linkId).stream()
            .anyMatch(g -> g.getGrantType() == req.grantType() && g.getGrantCode().equals(req.grantCode()));
        if (dup) throw ApiException.duplicate("该授权已存在");
        return grants.save(new LinkAccessGrant(linkId, req.grantType(), req.grantCode()));
    }

    @Transactional
    public void deleteGrant(Long linkId, Long grantId) {
        LinkAccessGrant g = grants.findById(grantId)
            .filter(x -> x.getLinkId().equals(linkId))
            .orElseThrow(() -> ApiException.notFound("授权"));
        grants.delete(g);
    }

    private void apply(Link l, LinkRequest req) {
        l.setCode(req.code()); l.setNameZh(req.nameZh()); l.setNameEn(req.nameEn());
        l.setUrl(req.url()); l.setIcon(req.icon());
        l.setCategoryCode(req.categoryCode()); l.setStatusCode(req.statusCode());
        l.setSortOrder(req.sortOrder()); l.setOpenInNewTab(req.openInNewTabOrDefault());
    }

    private void requireEnum(EnumCategory category, String code) {
        if (enums.findByCategoryAndCode(category, code).isEmpty())
            throw ApiException.badRequest(category + " 不存在枚举值: " + code);
    }

    private void requireGrantCode(GrantRequest g) {
        EnumCategory cat = g.grantType() == GrantType.DEPARTMENT
            ? EnumCategory.DEPARTMENT : EnumCategory.ROLE;
        if (enums.findByCategoryAndCode(cat, g.grantCode()).isEmpty())
            throw ApiException.badRequest(cat + " 不存在枚举值: " + g.grantCode());
    }
}
