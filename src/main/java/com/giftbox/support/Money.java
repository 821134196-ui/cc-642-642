package com.giftbox.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金额工具：系统内部一律使用“分”(long) 存储与计算，展示时转元。 */
public final class Money {

    private Money() {
    }

    public static long yuan(String yuan) {
        return new BigDecimal(yuan).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact();
    }

    public static String yuan(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
