package com.cimportal.enumvalue;

import com.cimportal.common.error.ApiException;
import com.cimportal.enumvalue.dto.EnumValueRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EnumValueService {
    private final EnumValueRepository repo;
    public EnumValueService(EnumValueRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<EnumValue> listActive(EnumCategory category) {
        return repo.findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc(category);
    }

    @Transactional(readOnly = true)
    public List<EnumValue> listAll(EnumCategory category) {
        return repo.findByCategoryOrderBySortOrderAscIdAsc(category);
    }

    @Transactional
    public EnumValue create(EnumCategory category, EnumValueRequest req) {
        if (repo.existsByCategoryAndCode(category, req.code()))
            throw ApiException.duplicate("枚举值 code '" + req.code() + "' 在类别 " + category + " 下已存在");
        return repo.save(new EnumValue(category, req.code(), req.labelZh(), req.labelEn(),
            req.sortOrder(), req.activeOrDefault()));
    }

    @Transactional
    public EnumValue update(EnumCategory category, Long id, EnumValueRequest req) {
        EnumValue e = repo.findById(id).filter(x -> x.getCategory() == category)
            .orElseThrow(() -> ApiException.notFound("枚举值"));
        e.setLabelZh(req.labelZh());
        e.setLabelEn(req.labelEn());
        e.setSortOrder(req.sortOrder());
        e.setActive(req.activeOrDefault());
        return e;
    }

    @Transactional
    public void delete(EnumCategory category, Long id) {
        EnumValue e = repo.findById(id).filter(x -> x.getCategory() == category)
            .orElseThrow(() -> ApiException.notFound("枚举值"));
        repo.delete(e);
    }
}
