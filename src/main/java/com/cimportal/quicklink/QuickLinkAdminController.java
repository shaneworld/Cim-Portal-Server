package com.cimportal.quicklink;

import com.cimportal.quicklink.dto.QuickLinkRequest;
import com.cimportal.quicklink.dto.QuickLinkResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/quick-links")
public class QuickLinkAdminController {

    private final QuickLinkService service;

    public QuickLinkAdminController(QuickLinkService service) {
        this.service = service;
    }

    @GetMapping
    public List<QuickLinkResponse> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuickLinkResponse create(@Valid @RequestBody QuickLinkRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public QuickLinkResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public QuickLinkResponse update(@PathVariable Long id,
                                    @Valid @RequestBody QuickLinkRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
