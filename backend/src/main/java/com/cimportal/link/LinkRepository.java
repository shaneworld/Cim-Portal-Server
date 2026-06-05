package com.cimportal.link;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    boolean existsByCode(String code);
    Optional<Link> findByCode(String code);
    List<Link> findAllByOrderBySortOrderAscIdAsc();
}
