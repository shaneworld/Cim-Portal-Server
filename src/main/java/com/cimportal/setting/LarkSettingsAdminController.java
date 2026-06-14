package com.cimportal.setting;

import com.cimportal.setting.dto.LarkSettingsUpdateRequest;
import com.cimportal.setting.dto.LarkSettingsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** PORTAL_ADMIN-gated — path under /api/admin/** is secured by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/lark-settings")
public class LarkSettingsAdminController {

    private final SecuritySettingService service;

    public LarkSettingsAdminController(SecuritySettingService service) {
        this.service = service;
    }

    @GetMapping
    public LarkSettingsView get() {
        return service.larkSettingsView();
    }

    @PutMapping
    public LarkSettingsView update(@Valid @RequestBody LarkSettingsUpdateRequest req) {
        return service.updateLarkSettings(req);
    }
}
