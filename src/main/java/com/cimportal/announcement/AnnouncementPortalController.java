package com.cimportal.announcement;

import com.cimportal.announcement.dto.AnnouncementResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/portal")
public class AnnouncementPortalController {

    private final AnnouncementService service;

    public AnnouncementPortalController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping("/announcements")
    public List<AnnouncementResponse> effective() {
        return service.effective(Instant.now());
    }
}
