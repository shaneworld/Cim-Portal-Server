package com.cimportal.dutyline;

import com.cimportal.common.error.ApiException;
import com.cimportal.dutyline.dto.DutyLineRequest;
import com.cimportal.dutyline.dto.DutyLineResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DutyLineService {

    private final DutyLineRepository repo;

    public DutyLineService(DutyLineRepository repo) {
        this.repo = repo;
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
        return repo.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public DutyLineResponse create(DutyLineRequest req) {
        DutyLine line = new DutyLine(
            req.labelZh(), req.labelEn(), req.phone(),
            req.sortOrderOrDefault(), req.activeOrDefault()
        );
        return toResponse(repo.save(line));
    }

    @Transactional
    public DutyLineResponse update(Long id, DutyLineRequest req) {
        DutyLine line = repo.findById(id)
            .orElseThrow(() -> ApiException.notFound("值班电话"));
        line.setLabelZh(req.labelZh());
        line.setLabelEn(req.labelEn());
        line.setPhone(req.phone());
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

    private DutyLineResponse toResponse(DutyLine line) {
        return new DutyLineResponse(
            line.getId(), line.getLabelZh(), line.getLabelEn(),
            line.getPhone(), line.getSortOrder(), line.isActive()
        );
    }
}
