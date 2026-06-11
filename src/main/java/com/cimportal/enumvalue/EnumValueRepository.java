package com.cimportal.enumvalue;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnumValueRepository extends JpaRepository<EnumValue, Long> {
    List<EnumValue> findByCategoryOrderBySortOrderAscIdAsc(EnumCategory category);
    List<EnumValue> findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc(EnumCategory category);
    Optional<EnumValue> findByCategoryAndCode(EnumCategory category, String code);
    boolean existsByCategoryAndCode(EnumCategory category, String code);
    boolean existsByCategoryAndCodeAndActiveTrue(EnumCategory category, String code);
}
