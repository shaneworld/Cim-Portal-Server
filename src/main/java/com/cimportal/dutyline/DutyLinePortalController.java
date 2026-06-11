package com.cimportal.dutyline;

import com.cimportal.dutyline.dto.DutyLineResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal")
public class DutyLinePortalController {

    private final DutyLineService service;

    public DutyLinePortalController(DutyLineService service) {
        this.service = service;
    }

    @GetMapping("/duty-lines")
    public List<DutyLineResponse> activeOrdered() {
        return service.activeOrdered();
    }
}
