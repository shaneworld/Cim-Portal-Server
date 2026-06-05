package com.cimportal.label;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabelRepository extends JpaRepository<Label, Long> {
    boolean existsByLabelKey(String labelKey);
    List<Label> findByType(String type);
    List<Label> findAllByOrderByLabelKeyAsc();
}
