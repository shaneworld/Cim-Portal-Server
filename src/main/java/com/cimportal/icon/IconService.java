package com.cimportal.icon;

import com.cimportal.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Service
public class IconService {
    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
    private static final long MAX_BYTES = 512L * 1024;

    private final UploadedIconRepository repo;

    public IconService(UploadedIconRepository repo) { this.repo = repo; }

    @Transactional
    public UploadedIcon store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("文件为空");
        String ct = file.getContentType();
        if (ct == null || !ALLOWED.contains(ct)) throw ApiException.badRequest("不支持的图片类型: " + ct);
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("图片超过 512KB");
        try {
            return repo.save(new UploadedIcon(ct, file.getSize(), file.getBytes()));
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("读取文件失败");
        }
    }

    public UploadedIcon get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("图标"));
    }
}
