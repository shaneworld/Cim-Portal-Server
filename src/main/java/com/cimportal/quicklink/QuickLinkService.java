package com.cimportal.quicklink;

import com.cimportal.common.error.ApiException;
import com.cimportal.quicklink.dto.QuickLinkRequest;
import com.cimportal.quicklink.dto.QuickLinkResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuickLinkService {

    private final QuickLinkRepository repo;

    public QuickLinkService(QuickLinkRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<QuickLinkResponse> listAll() {
        return repo.findAllByOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public QuickLinkResponse getById(Long id) {
        return toResponse(repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("快捷链接")));
    }

    @Transactional(readOnly = true)
    public List<QuickLinkResponse> activeOrdered() {
        return repo.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public QuickLinkResponse create(QuickLinkRequest req) {
        QuickLink link = new QuickLink(
            req.labelZh(), req.labelEn(), req.url(), req.icon(),
            req.sortOrderOrDefault(), req.activeOrDefault()
        );
        return toResponse(repo.save(link));
    }

    @Transactional
    public QuickLinkResponse update(Long id, QuickLinkRequest req) {
        QuickLink link = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("快捷链接"));
        link.setLabelZh(req.labelZh());
        link.setLabelEn(req.labelEn());
        link.setUrl(req.url());
        link.setIcon(req.icon());
        link.setSortOrder(req.sortOrderOrDefault());
        link.setActive(req.activeOrDefault());
        return toResponse(link);
    }

    @Transactional
    public void delete(Long id) {
        QuickLink link = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("快捷链接"));
        repo.delete(link);
    }

    private QuickLinkResponse toResponse(QuickLink link) {
        return new QuickLinkResponse(
            link.getId(), link.getLabelZh(), link.getLabelEn(),
            link.getUrl(), link.getIcon(), link.getSortOrder(), link.isActive()
        );
    }
}
