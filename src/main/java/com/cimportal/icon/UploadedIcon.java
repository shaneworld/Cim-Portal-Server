package com.cimportal.icon;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "uploaded_icon")
public class UploadedIcon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "content_type", nullable = false, length = 64) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Lob @Column(name = "bytes", nullable = false) private byte[] bytes;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected UploadedIcon() {}

    public UploadedIcon(String contentType, long fileSize, byte[] bytes) {
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.bytes = bytes;
    }

    public Long getId() { return id; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public byte[] getBytes() { return bytes; }
}
