package com.cimportal.label;

import com.cimportal.label.LabelService.LabelEntry;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/i18n")
public class LabelI18nController {
    private final LabelService service;
    public LabelI18nController(LabelService service) { this.service = service; }

    @GetMapping("/labels")
    public Map<String, LabelEntry> labels() { return service.i18nMap(); }
}
