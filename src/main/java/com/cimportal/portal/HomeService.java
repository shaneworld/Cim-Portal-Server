package com.cimportal.portal;

import com.cimportal.auth.CurrentUser;
import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.favorite.FavoriteRepository;
import com.cimportal.group.PermissionGroupMemberRepository;
import com.cimportal.link.Link;
import com.cimportal.link.LinkAccessGrant;
import com.cimportal.link.LinkAccessGrantRepository;
import com.cimportal.link.LinkRepository;
import com.cimportal.portal.dto.HomeCategory;
import com.cimportal.portal.dto.HomeLink;
import com.cimportal.portal.dto.HomeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HomeService {
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final EnumValueRepository enums;
    private final PermissionGroupMemberRepository memberRepo;
    private final FavoriteRepository favoriteRepo;

    public HomeService(LinkRepository links, LinkAccessGrantRepository grants,
                       EnumValueRepository enums, PermissionGroupMemberRepository memberRepo,
                       FavoriteRepository favoriteRepo) {
        this.links = links;
        this.grants = grants;
        this.enums = enums;
        this.memberRepo = memberRepo;
        this.favoriteRepo = favoriteRepo;
    }

    @Transactional(readOnly = true)
    public HomeResponse resolveFor(CurrentUser user) {
        List<Link> all = links.findAllByOrderBySortOrderAscIdAsc();
        if (all.isEmpty()) return new HomeResponse(List.of());

        // Load the user's favorites up-front (one query)
        Set<Long> favIds = new HashSet<>(favoriteRepo.findLinkIdsByEmployeeId(user.employeeId()));

        // Resolve the current user's active group membership codes (union with dept/role grants)
        Set<String> groupCodes = new HashSet<>(
            memberRepo.findActiveGroupCodesByEmployeeId(user.employeeId()));

        Map<Long, List<LinkAccessGrant>> grantsByLink = grants
            .findByLinkIdIn(all.stream().map(Link::getId).toList())
            .stream().collect(Collectors.groupingBy(LinkAccessGrant::getLinkId));

        Map<String, EnumValue> categoryLabels = enums
            .findByCategoryOrderBySortOrderAscIdAsc(EnumCategory.LINK_CATEGORY)
            .stream().collect(Collectors.toMap(EnumValue::getCode, e -> e, (a, b) -> a, LinkedHashMap::new));

        Map<String, List<HomeLink>> byCategory = new LinkedHashMap<>();
        categoryLabels.keySet().forEach(code -> byCategory.put(code, new ArrayList<>()));

        for (Link l : all) {
            var g = grantsByLink.getOrDefault(l.getId(), List.of());
            boolean accessible = PermissionResolver.isVisible(user.departmentCode(), user.roleCode(), groupCodes, g);
            byCategory.computeIfAbsent(l.getCategoryCode(), k -> new ArrayList<>())
                .add(new HomeLink(l.getId(), l.getNameZh(), l.getNameEn(),
                    accessible ? l.getUrl() : null, l.getIcon(), l.getStatusCode(),
                    l.isOpenInNewTab(), l.getEnvironment(),
                    l.isLaunchApp(), accessible ? l.getDownloadUrl() : null,
                    accessible, favIds.contains(l.getId())));
        }

        List<HomeCategory> categories = new ArrayList<>();
        for (var entry : byCategory.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            EnumValue meta = categoryLabels.get(entry.getKey());
            String zh = meta != null ? meta.getLabelZh() : entry.getKey();
            String en = meta != null ? meta.getLabelEn() : entry.getKey();
            categories.add(new HomeCategory(entry.getKey(), zh, en, entry.getValue()));
        }
        return new HomeResponse(categories);
    }
}
