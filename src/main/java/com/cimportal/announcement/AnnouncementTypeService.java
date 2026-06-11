package com.cimportal.announcement;

import com.cimportal.announcement.dto.AnnouncementTypeRequest;
import com.cimportal.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AnnouncementTypeService {

    private static final Set<String> VALID_COLORS =
        Set.of("blue", "amber", "red", "green", "purple", "slate");

    private final AnnouncementTypeRepository repo;

    public AnnouncementTypeService(AnnouncementTypeRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementType> listAll() {
        return repo.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public AnnouncementType getById(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("公告类型"));
    }

    @Transactional
    public AnnouncementType create(AnnouncementTypeRequest req) {
        if (repo.existsByCode(req.code()))
            throw ApiException.duplicate("类型 code '" + req.code() + "' 已存在");
        validateColor(req.color());
        return repo.save(new AnnouncementType(
            req.code(), req.labelZh(), req.labelEn(),
            req.color(), req.icon(), req.sortOrder(), req.activeOrDefault()
        ));
    }

    @Transactional
    public AnnouncementType update(Long id, AnnouncementTypeRequest req) {
        AnnouncementType t = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("公告类型"));
        validateColor(req.color());
        t.setLabelZh(req.labelZh());
        t.setLabelEn(req.labelEn());
        t.setColor(req.color());
        t.setIcon(req.icon());
        t.setSortOrder(req.sortOrder());
        t.setActive(req.activeOrDefault());
        return t;
    }

    @Transactional
    public void delete(Long id) {
        AnnouncementType t = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("公告类型"));
        repo.delete(t);
    }

    private void validateColor(String color) {
        if (!VALID_COLORS.contains(color))
            throw ApiException.badRequest("颜色 '" + color + "' 无效，允许值: " + VALID_COLORS);
    }
}
