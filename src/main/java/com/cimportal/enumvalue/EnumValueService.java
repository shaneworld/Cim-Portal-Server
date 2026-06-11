package com.cimportal.enumvalue;

import com.cimportal.common.error.ApiException;
import com.cimportal.enumvalue.dto.EnumValueRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class EnumValueService {

    private static final Set<String> VALID_COLORS =
        Set.of("blue", "amber", "red", "green", "purple", "slate");

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
        validateColorForCategory(category, req.color());
        EnumValue e = new EnumValue(category, req.code(), req.labelZh(), req.labelEn(),
            req.sortOrder(), req.activeOrDefault());
        e.setColor(req.color());
        e.setIcon(req.icon());
        return repo.save(e);
    }

    @Transactional
    public EnumValue update(EnumCategory category, Long id, EnumValueRequest req) {
        EnumValue e = repo.findById(id).filter(x -> x.getCategory() == category)
            .orElseThrow(() -> ApiException.notFound("枚举值"));
        validateColorForCategory(category, req.color());
        e.setLabelZh(req.labelZh());
        e.setLabelEn(req.labelEn());
        e.setSortOrder(req.sortOrder());
        e.setActive(req.activeOrDefault());
        e.setColor(req.color());
        e.setIcon(req.icon());
        return e;
    }

    private void validateColorForCategory(EnumCategory category, String color) {
        if (category == EnumCategory.ANNOUNCEMENT_TYPE && color != null && !VALID_COLORS.contains(color))
            throw ApiException.badRequest("颜色 '" + color + "' 无效，允许值: " + VALID_COLORS);
    }

    @Transactional
    public void delete(EnumCategory category, Long id) {
        EnumValue e = repo.findById(id).filter(x -> x.getCategory() == category)
            .orElseThrow(() -> ApiException.notFound("枚举值"));
        repo.delete(e);
    }
}
