package com.cimportal.icon;

import com.cimportal.icon.dto.IconUploadResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class IconController {
    private final IconService svc;

    public IconController(IconService svc) { this.svc = svc; }

    @PostMapping("/api/admin/icons")
    public ResponseEntity<IconUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        var i = svc.store(file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new IconUploadResponse(i.getId(), "upload:" + i.getId()));
    }

    @GetMapping("/api/icons/{id}")
    public ResponseEntity<byte[]> serve(@PathVariable Long id) {
        var i = svc.get(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(i.getContentType()))
            .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
            .body(i.getBytes());
    }
}
