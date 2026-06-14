package com.cimportal.setting;

import com.cimportal.setting.dto.DutySettingsUpdateRequest;
import com.cimportal.setting.dto.DutySettingsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** PORTAL_ADMIN-gated — path under /api/admin/** is secured by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/duty-settings")
public class DutySettingsAdminController {

    private final SecuritySettingService service;

    public DutySettingsAdminController(SecuritySettingService service) {
        this.service = service;
    }

    @GetMapping
    public DutySettingsView get() {
        return service.dutySettingsView();
    }

    @PutMapping
    public DutySettingsView update(@Valid @RequestBody DutySettingsUpdateRequest req) {
        return service.updateDutySettings(req);
    }
}
