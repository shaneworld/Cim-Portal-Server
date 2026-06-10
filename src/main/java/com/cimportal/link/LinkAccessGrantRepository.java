package com.cimportal.link;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LinkAccessGrantRepository extends JpaRepository<LinkAccessGrant, Long> {
    List<LinkAccessGrant> findByLinkId(Long linkId);
    List<LinkAccessGrant> findByLinkIdIn(List<Long> linkIds);
    void deleteByLinkId(Long linkId);
}
