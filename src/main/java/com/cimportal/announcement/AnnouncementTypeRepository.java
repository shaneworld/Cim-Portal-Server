package com.cimportal.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementTypeRepository extends JpaRepository<AnnouncementType, Long> {

    Optional<AnnouncementType> findByCode(String code);

    boolean existsByCode(String code);

    List<AnnouncementType> findAllByOrderBySortOrderAsc();
}
