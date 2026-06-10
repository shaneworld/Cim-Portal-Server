package com.cimportal.enumvalue;

import com.cimportal.enumvalue.dto.EnumValueRequest;
import com.cimportal.enumvalue.dto.EnumValueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/enums")
public class EnumAdminController {
    private final EnumValueService service;
    public EnumAdminController(EnumValueService service) { this.service = service; }

    @GetMapping("/{category}")
    public List<EnumValueResponse> list(@PathVariable EnumCategory category) {
        return service.listAll(category).stream().map(EnumValueResponse::of).toList();
    }

    @PostMapping("/{category}")
    @ResponseStatus(HttpStatus.CREATED)
    public EnumValueResponse create(@PathVariable EnumCategory category, @Valid @RequestBody EnumValueRequest req) {
        return EnumValueResponse.of(service.create(category, req));
    }

    @PutMapping("/{category}/{id}")
    public EnumValueResponse update(@PathVariable EnumCategory category, @PathVariable Long id,
                                    @Valid @RequestBody EnumValueRequest req) {
        return EnumValueResponse.of(service.update(category, id, req));
    }

    @DeleteMapping("/{category}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable EnumCategory category, @PathVariable Long id) {
        service.delete(category, id);
    }
}
