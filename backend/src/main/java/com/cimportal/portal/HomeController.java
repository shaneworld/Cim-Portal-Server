package com.cimportal.portal;

import com.cimportal.auth.CurrentUserService;
import com.cimportal.portal.dto.HomeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class HomeController {
    private final HomeService home;
    private final CurrentUserService currentUser;
    public HomeController(HomeService home, CurrentUserService currentUser) {
        this.home = home; this.currentUser = currentUser;
    }

    @GetMapping("/home")
    public HomeResponse home() { return home.resolveFor(currentUser.require()); }
}
