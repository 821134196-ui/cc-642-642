package com.departmentstore.giftbox.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 组合价分摊：总和必须严格等于组合价 */
class MoneyAllocationTest {

    @Test
    void sumsToTotalExactly() {
        // 锦绣茶礼：组合价 ¥588.00，权重 = 零售标价 × 数量
        long[] weights = {26800, 22800, 15800, 5800 * 2L, 2000, 3000};
        long[] result = MoneyAllocation.allocate(58800, weights);
        long sum = 0;
        for (long v : result) {
            sum += v;
        }
        assertEquals(58800, sum, "分摊总和必须等于组合价");
    }

    @Test
    void largestRemainderDistributesLeftoverCents() {
        // 100 按 1:1:1 分摊 → 34/33/33，余数补给第一行（确定性）
        assertArrayEquals(new long[]{34, 33, 33}, MoneyAllocation.allocate(100, new long[]{1, 1, 1}));
    }

    @Test
    void eachShareWithinOneCentOfExact() {
        long[] weights = {26800, 22800, 15800, 11600, 2000, 3000};
        long totalWeight = 0;
        for (long w : weights) {
            totalWeight += w;
        }
        long[] result = MoneyAllocation.allocate(58800, weights);
        for (int i = 0; i < weights.length; i++) {
            double exact = 58800.0 * weights[i] / totalWeight;
            assertTrue(Math.abs(result[i] - exact) < 1.0,
                    "每行分摊与精确值差应小于 1 分: " + result[i] + " vs " + exact);
        }
    }

    @Test
    void zeroTotalAllocatesZeros() {
        assertArrayEquals(new long[]{0, 0}, MoneyAllocation.allocate(0, new long[]{5, 7}));
    }

    @Test
    void rejectsEmptyOrZeroWeights() {
        assertThrows(IllegalArgumentException.class, () -> MoneyAllocation.allocate(100, new long[]{}));
        assertThrows(IllegalArgumentException.class, () -> MoneyAllocation.allocate(100, new long[]{0, 0}));
    }

    @Test
    void deductionForPartialAndFull() {
        // 行分摊 8318 分 / 2 件 → 每件 4159；全扣时必须等于 8318（余数被吸收）
        assertEquals(4159, MoneyAllocation.deductionFor(8318, 4159, 2, 1));
        assertEquals(8318, MoneyAllocation.deductionFor(8318, 4159, 2, 2));
        assertEquals(0, MoneyAllocation.deductionFor(8318, 4159, 2, 0));
    }
}
