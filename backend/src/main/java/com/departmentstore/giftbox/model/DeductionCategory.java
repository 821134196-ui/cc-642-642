package com.departmentstore.giftbox.model;

/** 扣减类别：赠品缺失与商品破损等分别展示报价和依据 */
public enum DeductionCategory {
    PRODUCT_MISSING,    // 商品缺失
    PRODUCT_DAMAGED,    // 商品破损
    ACCESSORY_MISSING,  // 配件缺失
    ACCESSORY_DAMAGED,  // 配件破损
    GIFT_MISSING,       // 赠品缺失
    GIFT_DAMAGED        // 赠品破损
}
