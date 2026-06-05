package com.cimportal.label;

import com.cimportal.common.error.ApiException;
import com.cimportal.label.dto.LabelRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LabelService {
    private final LabelRepository repo;
    public LabelService(LabelRepository repo) { this.repo = repo; }

    public record LabelEntry(String zh, String en, String type) { }

    @Transactional(readOnly = true)
    public Map<String, LabelEntry> i18nMap() {
        Map<String, LabelEntry> map = new LinkedHashMap<>();
        for (Label l : repo.findAllByOrderByLabelKeyAsc())
            map.put(l.getLabelKey(), new LabelEntry(l.getTextZh(), l.getTextEn(), l.getType()));
        return map;
    }

    @Transactional(readOnly = true)
    public List<Label> list(String type) {
        return type == null ? repo.findAllByOrderByLabelKeyAsc() : repo.findByType(type);
    }

    @Transactional
    public Label create(LabelRequest req) {
        if (repo.existsByLabelKey(req.labelKey()))
            throw ApiException.duplicate("labelKey '" + req.labelKey() + "' 已存在");
        return repo.save(new Label(req.labelKey(), req.type(), req.textZh(), req.textEn()));
    }

    @Transactional
    public Label update(Long id, LabelRequest req) {
        Label l = repo.findById(id).orElseThrow(() -> ApiException.notFound("标签"));
        l.setType(req.type()); l.setTextZh(req.textZh()); l.setTextEn(req.textEn());
        return l;   // labelKey immutable
    }

    @Transactional
    public void delete(Long id) {
        Label l = repo.findById(id).orElseThrow(() -> ApiException.notFound("标签"));
        repo.delete(l);
    }
}
