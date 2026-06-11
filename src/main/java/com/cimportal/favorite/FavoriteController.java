package com.cimportal.favorite;

import com.cimportal.auth.CurrentUserService;
import com.cimportal.common.error.ApiException;
import com.cimportal.common.error.ErrorCode;
import com.cimportal.link.LinkRepository;
import com.cimportal.portal.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portal/favorites")
public class FavoriteController {

    private final FavoriteRepository favorites;
    private final LinkRepository links;
    private final PermissionService permissions;
    private final CurrentUserService currentUser;

    public FavoriteController(FavoriteRepository favorites,
                              LinkRepository links,
                              PermissionService permissions,
                              CurrentUserService currentUser) {
        this.favorites = favorites;
        this.links = links;
        this.permissions = permissions;
        this.currentUser = currentUser;
    }

    @PostMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@PathVariable Long linkId) {
        var user = currentUser.require();
        links.findById(linkId)
            .orElseThrow(() -> ApiException.notFound("链接 " + linkId));
        if (!permissions.isAccessible(user, linkId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权收藏此链接");
        }
        if (!favorites.existsByEmployeeIdAndLinkId(user.employeeId(), linkId)) {
            favorites.save(new Favorite(user.employeeId(), linkId));
        }
    }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable Long linkId) {
        var user = currentUser.require();
        favorites.deleteByEmployeeIdAndLinkId(user.employeeId(), linkId);
    }
}
