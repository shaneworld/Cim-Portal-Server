package com.cimportal.announcement;

import com.cimportal.announcement.dto.AnnouncementRequest;
import com.cimportal.announcement.dto.AnnouncementResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/announcements")
public class AnnouncementAdminController {

    private final AnnouncementService service;

    public AnnouncementAdminController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnnouncementResponse> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse create(@Valid @RequestBody AnnouncementRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public AnnouncementResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public AnnouncementResponse update(@PathVariable Long id,
                                       @Valid @RequestBody AnnouncementRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
