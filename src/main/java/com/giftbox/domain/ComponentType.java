package com.giftbox.domain;

/** 组件类型：商品 / 配件 / 赠品 */
public enum ComponentType {
    PRODUCT("商品"),
    ACCESSORY("配件"),
    GIFT("赠品");

    public final String label;

    ComponentType(String label) {
        this.label = label;
    }
}
