package com.cimportal.dutyline;

import com.cimportal.common.error.ApiException;
import com.cimportal.dutyline.dto.DutyLineRequest;
import com.cimportal.dutyline.dto.DutyLineResponse;
import com.cimportal.setting.SecuritySetting;
import com.cimportal.setting.SecuritySettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DutyLineService {

    private final DutyLineRepository repo;
    private final OnDutyCache onDutyCache;
    private final SecuritySettingService settings;

    public DutyLineService(DutyLineRepository repo, OnDutyCache onDutyCache, SecuritySettingService settings) {
        this.repo = repo;
        this.onDutyCache = onDutyCache;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<DutyLineResponse> listAll() {
        return repo.findAllByOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public DutyLineResponse getById(Long id) {
        return toResponse(repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("值班电话")));
    }

    @Transactional(readOnly = true)
    public List<DutyLineResponse> activeOrdered() {
        SecuritySetting s = settings.get();
        String baseUrl = s.getDutyApiBaseUrl();
        String apiKey = s.getDutyApiKey();
        return repo.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(dl -> resolve(dl, baseUrl, apiKey))
            .toList();
    }

    @Transactional
    public DutyLineResponse create(DutyLineRequest req) {
        DutyLine line = new DutyLine(
            req.labelZh(), req.labelEn(), req.phone(),
            req.sortOrderOrDefault(), req.activeOrDefault()
        );
        line.setScheduleName(req.scheduleName());
        return toResponse(repo.save(line));
    }

    @Transactional
    public DutyLineResponse update(Long id, DutyLineRequest req) {
        DutyLine line = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("值班电话"));
        line.setLabelZh(req.labelZh());
        line.setLabelEn(req.labelEn());
        line.setPhone(req.phone());
        line.setScheduleName(req.scheduleName());
        line.setSortOrder(req.sortOrderOrDefault());
        line.setActive(req.activeOrDefault());
        return toResponse(line);
    }

    @Transactional
    public void delete(Long id) {
        DutyLine line = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("值班电话"));
        repo.delete(line);
    }

    /** Static/admin mapping — never calls the external system; dutyName is null. */
    private DutyLineResponse toResponse(DutyLine line) {
        return new DutyLineResponse(
            line.getId(), line.getLabelZh(), line.getLabelEn(),
            line.getPhone(), line.getSortOrder(), line.isActive(),
            line.getScheduleName(), null
        );
    }

    /** Portal mapping — resolves the live on-duty person, falling back to the static phone. */
    private DutyLineResponse resolve(DutyLine dl, String baseUrl, String apiKey) {
        String name = null;
        String phone = dl.getPhone();
        if (dl.getScheduleName() != null && !dl.getScheduleName().isBlank()) {
            Optional<OnDutyPerson> p = onDutyCache.get(baseUrl, apiKey, dl.getScheduleName());
            if (p.isPresent()) {
                name = p.get().name();
                phone = p.get().phone();
            }
        }
        return new DutyLineResponse(
            dl.getId(), dl.getLabelZh(), dl.getLabelEn(),
            phone, dl.getSortOrder(), dl.isActive(),
            dl.getScheduleName(), name
        );
    }
}
