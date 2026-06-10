package com.cimportal.setting;

import com.cimportal.setting.dto.AdminSettingView;
import com.cimportal.setting.dto.SecuritySettingUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** PORTAL_ADMIN-gated — path under /api/admin/** is secured by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/security-settings")
public class SecuritySettingAdminController {

    private final SecuritySettingService service;

    public SecuritySettingAdminController(SecuritySettingService service) {
        this.service = service;
    }

    @GetMapping
    public AdminSettingView get() {
        return service.adminView();
    }

    @PutMapping
    public AdminSettingView update(@Valid @RequestBody SecuritySettingUpdateRequest req) {
        return service.update(req);
    }
}
