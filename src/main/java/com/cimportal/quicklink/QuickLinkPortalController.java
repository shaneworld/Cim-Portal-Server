package com.cimportal.quicklink;

import com.cimportal.quicklink.dto.QuickLinkResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal")
public class QuickLinkPortalController {

    private final QuickLinkService service;

    public QuickLinkPortalController(QuickLinkService service) {
        this.service = service;
    }

    @GetMapping("/quick-links")
    public List<QuickLinkResponse> activeOrdered() {
        return service.activeOrdered();
    }
}
