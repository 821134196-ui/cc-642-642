package com.departmentstore.giftbox.model;

/** 退货单状态：核对中 → 已报价（待顾客确认）→ 已退款 */
public enum ReturnStatus {
    INSPECTING,
    QUOTED,
    REFUNDED
}
