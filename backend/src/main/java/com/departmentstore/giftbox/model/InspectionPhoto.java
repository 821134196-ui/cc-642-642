package com.departmentstore.giftbox.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/** 检查照片：文件存本地磁盘，此处记录元数据，挂在退货单下随订单回看 */
@Entity
public class InspectionPhoto extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    public ReturnRequest returnRequest;

    /** 磁盘上的文件名（photos 目录下） */
    @Column(nullable = false)
    public String filename;

    public String originalName;

    @Column(length = 300)
    public String note;

    @Column(nullable = false)
    public LocalDateTime uploadedAt;
}
