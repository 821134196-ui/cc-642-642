package com.giftbox.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/** 检查照片：文件本地保存，元数据挂在退货单（原订单）下方便回看。 */
@Entity
public class InspectionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReturnCase returnCase;

    /** 本地保存的文件名（相对 photo-dir） */
    private String fileName;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private String uploadedBy;
    private LocalDateTime uploadedAt;

    public InspectionPhoto() {
    }

    public InspectionPhoto(ReturnCase returnCase, String fileName, String originalName,
                           String contentType, long sizeBytes, String uploadedBy,
                           LocalDateTime uploadedAt) {
        this.returnCase = returnCase;
        this.fileName = fileName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public ReturnCase getReturnCase() { return returnCase; }
    public String getFileName() { return fileName; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
