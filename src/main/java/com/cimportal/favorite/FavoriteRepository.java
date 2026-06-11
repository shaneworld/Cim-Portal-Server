package com.cimportal.favorite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    @Query("select f.linkId from Favorite f where f.employeeId = ?1")
    List<Long> findLinkIdsByEmployeeId(String employeeId);

    boolean existsByEmployeeIdAndLinkId(String employeeId, Long linkId);

    @Modifying
    @Transactional
    void deleteByEmployeeIdAndLinkId(String employeeId, Long linkId);
}
