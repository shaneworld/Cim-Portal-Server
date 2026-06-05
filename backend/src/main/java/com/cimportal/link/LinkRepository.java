package com.cimportal.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    boolean existsByCode(String code);
    Optional<Link> findByCode(String code);
    List<Link> findAllByOrderBySortOrderAscIdAsc();

    @Query("""
        SELECT l FROM Link l
        WHERE (:categoryCode IS NULL OR l.categoryCode = :categoryCode)
          AND (:statusCode IS NULL OR l.statusCode = :statusCode)
          AND (:q IS NULL OR LOWER(l.code) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(l.nameZh) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(l.nameEn) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY l.sortOrder ASC, l.id ASC
        """)
    List<Link> search(@Param("categoryCode") String categoryCode,
                      @Param("statusCode") String statusCode,
                      @Param("q") String q);
}
