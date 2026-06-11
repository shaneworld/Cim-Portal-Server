package com.cimportal.announcement;

import com.cimportal.announcement.dto.AnnouncementTypeRequest;
import com.cimportal.announcement.dto.AnnouncementTypeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/announcement-types")
public class AnnouncementTypeAdminController {

    private final AnnouncementTypeService service;

    public AnnouncementTypeAdminController(AnnouncementTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnnouncementTypeResponse> list() {
        return service.listAll().stream().map(AnnouncementTypeResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementTypeResponse create(@Valid @RequestBody AnnouncementTypeRequest req) {
        return AnnouncementTypeResponse.of(service.create(req));
    }

    @GetMapping("/{id}")
    public AnnouncementTypeResponse get(@PathVariable Long id) {
        return AnnouncementTypeResponse.of(service.getById(id));
    }

    @PutMapping("/{id}")
    public AnnouncementTypeResponse update(@PathVariable Long id,
                                           @Valid @RequestBody AnnouncementTypeRequest req) {
        return AnnouncementTypeResponse.of(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
