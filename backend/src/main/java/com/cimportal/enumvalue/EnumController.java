package com.cimportal.enumvalue;

import com.cimportal.enumvalue.dto.EnumValueResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/enums")
public class EnumController {
    private final EnumValueService service;
    public EnumController(EnumValueService service) { this.service = service; }

    @GetMapping("/{category}")
    public List<EnumValueResponse> list(@PathVariable EnumCategory category) {
        return service.listActive(category).stream().map(EnumValueResponse::of).toList();
    }
}
