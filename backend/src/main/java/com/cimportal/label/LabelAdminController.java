package com.cimportal.label;

import com.cimportal.label.dto.LabelRequest;
import com.cimportal.label.dto.LabelResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/labels")
public class LabelAdminController {
    private final LabelService service;
    public LabelAdminController(LabelService service) { this.service = service; }

    @GetMapping
    public List<LabelResponse> list(@RequestParam(required = false) String type) {
        return service.list(type).stream().map(LabelResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse create(@Valid @RequestBody LabelRequest req) {
        return LabelResponse.of(service.create(req));
    }

    @PutMapping("/{id}")
    public LabelResponse update(@PathVariable Long id, @Valid @RequestBody LabelRequest req) {
        return LabelResponse.of(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
