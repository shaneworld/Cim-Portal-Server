package com.cimportal.portal;

import com.cimportal.setting.SecuritySettingService;
import com.cimportal.setting.dto.PublicConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (anonymous) endpoint — SPA reads this on startup to know if SSO is active. */
@RestController
@RequestMapping("/api/portal")
public class PortalConfigController {

    private final SecuritySettingService service;

    public PortalConfigController(SecuritySettingService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public PublicConfig config() {
        return service.publicView();
    }
}
