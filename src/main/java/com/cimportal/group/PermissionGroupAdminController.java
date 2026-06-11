package com.cimportal.group;

import com.cimportal.group.dto.GroupRequest;
import com.cimportal.group.dto.GroupResponse;
import com.cimportal.group.dto.MembersReplaceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permission-groups")
public class PermissionGroupAdminController {
    private final PermissionGroupService svc;

    public PermissionGroupAdminController(PermissionGroupService svc) { this.svc = svc; }

    @GetMapping
    public List<GroupResponse> list() { return svc.list(); }

    @GetMapping("/{id}")
    public GroupResponse get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.create(r));
    }

    @PutMapping("/{id}")
    public GroupResponse update(@PathVariable Long id, @Valid @RequestBody GroupRequest r) {
        return svc.update(id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public List<String> members(@PathVariable Long id) { return svc.membersOf(id); }

    @PutMapping("/{id}/members")
    public List<String> replaceMembers(@PathVariable Long id, @RequestBody MembersReplaceRequest req) {
        return svc.replaceMembers(id, req.safe());
    }
}
