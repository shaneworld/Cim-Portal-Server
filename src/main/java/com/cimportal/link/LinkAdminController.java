package com.cimportal.link;

import com.cimportal.link.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/links")
public class LinkAdminController {
    private final LinkService service;
    public LinkAdminController(LinkService service) { this.service = service; }

    @GetMapping
    public List<LinkResponse> list(@RequestParam(required = false) String categoryCode,
                                   @RequestParam(required = false) String statusCode,
                                   @RequestParam(required = false) String q) {
        return service.search(categoryCode, statusCode, q).stream()
            .map(l -> service.toResponse(l, List.of())).toList();
    }

    @GetMapping("/{id}")
    public LinkResponse get(@PathVariable Long id) {
        var l = service.get(id);
        var grants = service.grantsOf(id).stream().map(GrantResponse::of).toList();
        return service.toResponse(l, grants);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse create(@Valid @RequestBody LinkRequest req) {
        return service.toResponse(service.create(req), List.of());
    }

    @PutMapping("/{id}")
    public LinkResponse update(@PathVariable Long id, @Valid @RequestBody LinkRequest req) {
        return service.toResponse(service.update(id, req), List.of());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/{id}/grants")
    public List<GrantResponse> grants(@PathVariable Long id) {
        return service.grantsOf(id).stream().map(GrantResponse::of).toList();
    }

    @PutMapping("/{id}/grants")
    public List<GrantResponse> replaceGrants(@PathVariable Long id, @Valid @RequestBody GrantsReplaceRequest req) {
        return service.replaceGrants(id, req.safeGrants()).stream().map(GrantResponse::of).toList();
    }

    @PostMapping("/{id}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse addGrant(@PathVariable Long id, @Valid @RequestBody GrantRequest req) {
        return GrantResponse.of(service.addGrant(id, req));
    }

    @DeleteMapping("/{id}/grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGrant(@PathVariable Long id, @PathVariable Long grantId) {
        service.deleteGrant(id, grantId);
    }
}
