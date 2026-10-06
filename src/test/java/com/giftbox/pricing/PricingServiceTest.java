package com.giftbox.pricing;

import com.giftbox.domain.ComponentType;
import com.giftbox.domain.CustomerOrder;
import com.giftbox.domain.GiftBox;
import com.giftbox.domain.ReturnLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 纯单元测试：组合价分摊、快照价、赠品缺失/商品破损分别扣减、恒等勾稽。 */
class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    private record Fixture(CustomerOrder order, List<ReturnLine> lines, GiftBox box) {
    }

    private Fixture build(String comboYuan, long[] quotes, int[] qtys, ComponentType[] types) {
        GiftBox box = new GiftBox("HB-T", "测试礼盒", yuan(comboYuan));
        for (int i = 0; i < quotes.length; i++) {
            box.addComponent(types[i], "组件" + (i + 1), quotes[i], qtys[i], i + 1);
        }
        CustomerOrder order = new CustomerOrder("SO-T", "测试顾客", "A柜", box,
                yuan(comboYuan), null);
        List<ReturnLine> lines = new ArrayList<>();
        int idx = 0;
        for (var c : box.getComponents()) {
            lines.add(new ReturnLine(null, c, idx++));
        }
        return new Fixture(order, lines, box);
    }

    private static long yuan(String v) {
        return new java.math.BigDecimal(v).movePointRight(2).longValueExact();
    }

    private void mark(ReturnLine line, int intact, int damaged, int missing) {
        line.setIntactQty(intact);
        line.setDamagedQty(damaged);
        line.setMissingQty(missing);
    }

    @Test
    void 全完好时退款等于组合价扣减为零() {
        var f = build("500.00",
                new long[]{38000, 18000, 3000, 1000, 6000, 2000},
                new int[]{1, 1, 1, 1, 1, 2},
                new ComponentType[]{ComponentType.PRODUCT, ComponentType.PRODUCT,
                        ComponentType.ACCESSORY, ComponentType.ACCESSORY,
                        ComponentType.GIFT, ComponentType.GIFT});

        var q = pricing.quote(f.order(), f.lines());

        assertEquals(50000, q.comboPriceCents());
        assertEquals(50000, q.refundCents());
        assertEquals(0, q.deductionCents());
        assertEquals(q.comboPriceCents(), q.refundCents() + q.deductionCents());
    }

    @Test
    void 分摊到各组件之和严格等于组合价且按权重() {
        var f = build("480.00", // 优惠成交快照价
                new long[]{38000, 18000, 3000, 1000, 6000, 2000},
                new int[]{1, 1, 1, 1, 1, 2},
                new ComponentType[]{ComponentType.PRODUCT, ComponentType.PRODUCT,
                        ComponentType.ACCESSORY, ComponentType.ACCESSORY,
                        ComponentType.GIFT, ComponentType.GIFT});

        var q = pricing.quote(f.order(), f.lines());

        long sumAllocated = q.lines().stream().mapToLong(PricingService.LineQuote::allocatedCents).sum();
        assertEquals(48000, sumAllocated, "各行分摊额之和必须等于售出组合价快照 480");
        // 总权重 700（380+180+30+10+60+20+20）：480*380/700≈260.57，480*180/700≈123.43
        var perfume = q.lines().get(0);
        var lipstick = q.lines().get(1);
        assertTrue(perfume.allocatedCents() > lipstick.allocatedCents());
        assertEquals(26057, perfume.allocatedCents());
        assertEquals(12343, lipstick.allocatedCents());
        // 最大余数法把 3 分钱余额补给余数最大的三件（口红/贺卡/小样），香水行保持 floor
        long sumRefund = q.lines().stream().mapToLong(PricingService.LineQuote::refundCents).sum();
        assertEquals(48000, sumRefund);
    }

    @Test
    void 赠品缺失与商品破损分别列示且加总等于扣减() {
        var f = build("300.00",
                new long[]{15000, 12000, 1500, 3000, 1500},
                new int[]{1, 1, 1, 1, 1},
                new ComponentType[]{ComponentType.PRODUCT, ComponentType.PRODUCT,
                        ComponentType.ACCESSORY, ComponentType.GIFT, ComponentType.GIFT});
        var lines = f.lines();
        // 第一件商品破损 1；第 4 件（赠品蜂蜜）缺失 1
        mark(lines.get(0), 0, 1, 0);
        mark(lines.get(3), 0, 0, 1);

        var q = pricing.quote(f.order(), f.lines());

        // 恒等
        assertEquals(30000, q.refundCents() + q.deductionCents());

        var productLine = q.lines().get(0);
        var giftLine = q.lines().get(3);

        // 商品破损扣减项
        assertEquals(1, productLine.charges().size());
        var damaged = productLine.charges().get(0);
        assertEquals("商品破损", damaged.title());
        assertEquals(15000, damaged.unitQuoteCents());
        assertTrue(damaged.basis().contains("组合价分摊"));

        // 赠品缺失扣减项
        assertEquals(1, giftLine.charges().size());
        var missing = giftLine.charges().get(0);
        assertEquals("赠品缺失", missing.title());
        assertEquals(3000, missing.unitQuoteCents());

        // 各行扣减 = 其扣减项之和；总扣减 = 各行之和
        long chargeSum = q.lines().stream()
                .flatMap(l -> l.charges().stream())
                .mapToLong(PricingService.ChargeItem::amountCents).sum();
        long lineDeductionSum = q.lines().stream()
                .mapToLong(PricingService.LineQuote::deductionCents).sum();
        assertEquals(q.deductionCents(), chargeSum);
        assertEquals(q.deductionCents(), lineDeductionSum);
        assertEquals(productLine.deductionCents(), damaged.amountCents());
        assertEquals(giftLine.deductionCents(), missing.amountCents());
    }

    @Test
    void 多件组件部分破损只扣坏件分摊() {
        var f = build("100.00",
                new long[]{5000, 5000},
                new int[]{2, 2},
                new ComponentType[]{ComponentType.PRODUCT, ComponentType.GIFT});
        var lines = f.lines();
        // 第一件商品 2 件中破损 1 件
        mark(lines.get(0), 1, 1, 0);

        var q = pricing.quote(f.lines().isEmpty() ? f.order() : f.order(), f.lines());

        // 4 个单位权重相同 → 每单位分摊 25.00；坏 1 件扣 25，退 75
        assertEquals(2500, q.deductionCents());
        assertEquals(7500, q.refundCents());
        assertEquals(10000, q.refundCents() + q.deductionCents());
    }

    @Test
    void 零元极端情况仍能均摊且恒等() {
        var f = build("10.00",
                new long[]{0, 0},
                new int[]{1, 3},
                new ComponentType[]{ComponentType.GIFT, ComponentType.GIFT});
        mark(f.lines().get(1), 2, 1, 0);

        var q = pricing.quote(f.order(), f.lines());

        assertEquals(1000, q.refundCents() + q.deductionCents());
        // 4 件均摊每件 2.50 → 3 件退 7.50，1 件扣 2.50
        assertEquals(250, q.deductionCents());
        assertEquals(750, q.refundCents());
    }
}
