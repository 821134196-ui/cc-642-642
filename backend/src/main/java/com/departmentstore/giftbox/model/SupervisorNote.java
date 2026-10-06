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

/** 主管意见：保留在退货单下，随原订单回看 */
@Entity
public class SupervisorNote extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    public ReturnRequest returnRequest;

    @Column(nullable = false)
    public String author;

    @Column(nullable = false, length = 2000)
    public String text;

    @Column(nullable = false)
    public LocalDateTime createdAt;
}
