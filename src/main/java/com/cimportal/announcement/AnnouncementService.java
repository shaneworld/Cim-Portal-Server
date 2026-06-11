package com.cimportal.announcement;

import com.cimportal.announcement.dto.AnnouncementRequest;
import com.cimportal.announcement.dto.AnnouncementResponse;
import com.cimportal.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementRepository repo;
    private final AnnouncementTypeRepository typeRepo;

    public AnnouncementService(AnnouncementRepository repo, AnnouncementTypeRepository typeRepo) {
        this.repo = repo;
        this.typeRepo = typeRepo;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listAll() {
        return repo.findAllByOrderByPinnedDescCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getById(Long id) {
        return toResponse(repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("公告")));
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementRequest req) {
        validateTypeCode(req.typeCode());
        Announcement a = new Announcement(
            req.titleZh(), req.titleEn(), req.bodyZh(), req.bodyEn(),
            req.typeCode(), req.pinnedOrDefault(), req.startsAt(), req.endsAt(),
            req.activeOrDefault()
        );
        return toResponse(repo.save(a));
    }

    @Transactional
    public AnnouncementResponse update(Long id, AnnouncementRequest req) {
        Announcement a = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("公告"));
        validateTypeCode(req.typeCode());
        a.setTitleZh(req.titleZh());
        a.setTitleEn(req.titleEn());
        a.setBodyZh(req.bodyZh());
        a.setBodyEn(req.bodyEn());
        a.setTypeCode(req.typeCode());
        a.setPinned(req.pinnedOrDefault());
        a.setStartsAt(req.startsAt());
        a.setEndsAt(req.endsAt());
        a.setActive(req.activeOrDefault());
        return toResponse(a);
    }

    @Transactional
    public void delete(Long id) {
        Announcement a = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("公告"));
        repo.delete(a);
    }

    /** Returns announcements that are currently effective at the given instant. */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> effective(Instant now) {
        return repo.findAllByOrderByPinnedDescCreatedAtDesc().stream()
            .filter(a -> a.isActive()
                && (a.getStartsAt() == null || !a.getStartsAt().isAfter(now))
                && (a.getEndsAt()   == null || !a.getEndsAt().isBefore(now)))
            .map(this::toResponse)
            .toList();
    }

    private void validateTypeCode(String typeCode) {
        AnnouncementType type = typeRepo.findByCode(typeCode)
            .orElseThrow(() -> ApiException.badRequest("公告类型 '" + typeCode + "' 不存在"));
        if (!type.isActive())
            throw ApiException.badRequest("公告类型 '" + typeCode + "' 已停用");
    }

    private AnnouncementResponse toResponse(Announcement a) {
        AnnouncementType type = typeRepo.findByCode(a.getTypeCode()).orElse(null);
        String color  = type != null ? type.getColor()   : "slate";
        String icon   = type != null ? type.getIcon()    : "info";
        String labelZh = type != null ? type.getLabelZh() : a.getTypeCode();
        String labelEn = type != null ? type.getLabelEn() : a.getTypeCode();
        return new AnnouncementResponse(
            a.getId(), a.getTitleZh(), a.getTitleEn(), a.getBodyZh(), a.getBodyEn(),
            a.getTypeCode(), labelZh, labelEn, color, icon,
            a.isPinned(), a.getStartsAt(), a.getEndsAt(), a.isActive(), a.getCreatedAt()
        );
    }
}
