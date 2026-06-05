package com.cimportal.label;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LabelRepository extends JpaRepository<Label, Long> {
    boolean existsByLabelKey(String labelKey);
    List<Label> findByType(String type);
    List<Label> findAllByOrderByLabelKeyAsc();

    @Query("""
        SELECT l FROM Label l
        WHERE (:type IS NULL OR l.type = :type)
          AND (:q IS NULL OR LOWER(l.labelKey) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(l.textZh) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(l.textEn) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY l.labelKey ASC
        """)
    List<Label> search(@Param("type") String type, @Param("q") String q);
}
