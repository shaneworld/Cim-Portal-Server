package com.cimportal.dutyline;

import com.cimportal.dutyline.dto.DutyLineRequest;
import com.cimportal.dutyline.dto.DutyLineResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/duty-lines")
public class DutyLineAdminController {

    private final DutyLineService service;

    public DutyLineAdminController(DutyLineService service) {
        this.service = service;
    }

    @GetMapping
    public List<DutyLineResponse> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DutyLineResponse create(@Valid @RequestBody DutyLineRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public DutyLineResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public DutyLineResponse update(@PathVariable Long id,
                                   @Valid @RequestBody DutyLineRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
