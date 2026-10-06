package com.departmentstore.giftbox.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：
 *  1. 金额分摊与退款一致性（退款 = 组合价 − 扣减明细）
 *  2. 不完整礼盒只入散件库存，库存里不出现未复原的整盒
 *  3. 完整且合格才恢复整盒
 *  4. 重复确认/重复回执不再退款、不再加库存
 *  5. 两个柜台同时办理同一订单：只有一笔有效退款和一次库存调整
 */
@QuarkusTest
class ReturnFlowTest {

    // ---------- 辅助 ----------

    private long itemIdOf(String orderNo) {
        return given().get("/api/orders/" + orderNo)
                .then().statusCode(200)
                .extract().<Integer>path("items[0].id").longValue();
    }

    private long createReturn(long itemId, String idemKey) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("orderItemId", itemId, "counterId", "COUNTER-T", "idempotencyKey", idemKey))
                .post("/api/returns")
                .then().statusCode(200)
                .extract().<Integer>path("id").longValue();
    }

    private Map<String, Map<String, Object>> linesBySku(long returnId) {
        List<Map<String, Object>> lines = given().get("/api/returns/" + returnId)
                .then().statusCode(200).extract().path("lines");
        Map<String, Map<String, Object>> bySku = new HashMap<>();
        for (Map<String, Object> l : lines) {
            bySku.put((String) l.get("sku"), l);
        }
        return bySku;
    }

    /** 按 sku 覆盖（received, intact, damaged），其余行保持全好 */
    private void setLines(long returnId, boolean passed, Map<String, int[]> overrides) {
        Map<String, Map<String, Object>> bySku = linesBySku(returnId);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : bySku.entrySet()) {
            Map<String, Object> l = e.getValue();
            int expected = (Integer) l.get("expectedQty");
            int[] o = overrides.getOrDefault(e.getKey(), new int[]{expected, expected, 0});
            lines.add(Map.of("lineId", l.get("id"), "receivedQty", o[0], "intactQty", o[1], "damagedQty", o[2]));
        }
        given().contentType(ContentType.JSON)
                .body(Map.of("lines", lines, "inspectionPassed", passed))
                .put("/api/returns/" + returnId + "/lines")
                .then().statusCode(200);
    }

    private Map<String, Object> quote(long returnId) {
        return given().post("/api/returns/" + returnId + "/quote")
                .then().statusCode(200).extract().as(Map.class);
    }

    private Map<String, Object> confirm(long returnId, String counterId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("counterId", counterId))
                .post("/api/returns/" + returnId + "/confirm")
                .then().statusCode(200).extract().as(Map.class);
    }

    private long invQty(String sku, String state) {
        List<Map<String, Object>> rows = given().get("/api/inventory")
                .then().statusCode(200).extract().path("rows");
        for (Map<String, Object> r : rows) {
            if (sku.equals(r.get("sku")) && state.equals(r.get("state"))) {
                return ((Number) r.get("qty")).longValue();
            }
        }
        return 0;
    }

    private long txnCountFor(long returnId) {
        List<Map<String, Object>> txns = given().get("/api/inventory")
                .then().statusCode(200).extract().path("recentTxns");
        return txns.stream().filter(t -> t.get("returnRequestId") != null
                && ((Number) t.get("returnRequestId")).longValue() == returnId).count();
    }

    private Map<String, Number> stats() {
        return given().get("/api/stats").then().statusCode(200).extract().as(Map.class);
    }

    private static long num(Map<String, Number> stats, String key) {
        return stats.get(key).longValue();
    }

    // ---------- 1. 分摊与退款一致 + 散件入库，整盒不变 ----------

    @Test
    void partialReturnRestocksLooseComponentsOnly() {
        long statsRefundsBefore = num(stats(), "refunds");
        long itemId = itemIdOf("ORD-1001");
        long returnId = createReturn(itemId, "it-partial-" + itemId);

        // 茶杯 1 件破损，贺卡（赠品）缺失，其余完好
        setLines(returnId, false, Map.of(
                "TEACUP", new int[]{2, 1, 1},
                "CARD", new int[]{0, 0, 0}));

        Map<String, Object> quoted = quote(returnId);
        long totalCombo = ((Number) quoted.get("totalComboCents")).longValue();
        List<Map<String, Object>> deductions = (List<Map<String, Object>>) quoted.get("deductions");
        long deductionSum = deductions.stream()
                .mapToLong(d -> ((Number) d.get("amountCents")).longValue()).sum();
        long quotedAmount = ((Number) quoted.get("quotedAmountCents")).longValue();

        // 退款金额 = 组合价 − 扣减明细
        assertEquals(totalCombo - deductionSum, quotedAmount, "退款金额必须与组合价和扣减明细一致");
        assertEquals(58800, totalCombo);
        assertEquals(4159 + 1434, deductionSum, "茶杯破损 4159 + 贺卡赠品缺失 1434");
        assertEquals(53207, quotedAmount);
        // 赠品缺失与商品/配件破损分别展示且带依据
        assertTrue(deductions.stream().anyMatch(d -> "GIFT_MISSING".equals(d.get("category"))));
        assertTrue(deductions.stream().anyMatch(d -> "ACCESSORY_DAMAGED".equals(d.get("category"))));
        assertTrue(deductions.stream().allMatch(d -> d.get("basis") != null
                && ((String) d.get("basis")).contains("依据")));

        long boxBefore = invQty("BOX-TEA", "SELLABLE");
        long teacupSellableBefore = invQty("TEACUP", "SELLABLE");

        Map<String, Object> confirmed = confirm(returnId, "COUNTER-T");
        Map<String, Object> refund = (Map<String, Object>) confirmed.get("refund");
        assertNotNull(refund.get("receiptNo"));
        assertEquals(53207L, ((Number) refund.get("amountCents")).longValue());

        // 散件各归各状态；未复原的整盒绝不能增加
        assertEquals(boxBefore, invQty("BOX-TEA", "SELLABLE"), "不完整礼盒不得恢复整盒库存");
        assertEquals(teacupSellableBefore + 1, invQty("TEACUP", "SELLABLE"), "完好茶杯入可售散件");
        assertEquals(1, invQty("TEACUP", "DAMAGED"), "破损茶杯入破损散件");
        assertEquals(1, invQty("TEA-RED", "SELLABLE"));
        assertEquals(0, invQty("CARD", "SELLABLE"), "缺失赠品不入库");
        assertEquals(statsRefundsBefore + 1, num(stats(), "refunds"));
    }

    // ---------- 2. 完整且合格 → 恢复整盒 ----------

    @Test
    void completeAndPassedRestoresWholeBox() {
        long itemId = itemIdOf("ORD-1002");
        long returnId = createReturn(itemId, "it-complete-" + itemId);
        setLines(returnId, true, Map.of());

        Map<String, Object> quoted = quote(returnId);
        assertEquals(19900L, ((Number) quoted.get("quotedAmountCents")).longValue(), "无扣减应全额退款");
        assertEquals(0, ((List<?>) quoted.get("deductions")).size());

        long boxBefore = invQty("BOX-FRUIT", "SELLABLE");
        Map<String, Object> confirmed = confirm(returnId, "COUNTER-T");
        assertEquals(19900L, ((Number) ((Map<String, Object>) confirmed.get("refund")).get("amountCents")).longValue());

        assertEquals(boxBefore + 1, invQty("BOX-FRUIT", "SELLABLE"), "完整合格礼盒应恢复整盒库存 +1");
        assertEquals(1, txnCountFor(returnId), "整盒复原只产生一条库存流水");
    }

    // ---------- 3. 重复确认 / 重复回执：不再退款、不加库存 ----------

    @Test
    void duplicateConfirmReturnsSameReceiptWithoutSideEffects() {
        long itemId = itemIdOf("ORD-1003");
        long returnId = createReturn(itemId, "it-dup-" + itemId);
        setLines(returnId, true, Map.of());
        quote(returnId);

        Map<String, Number> before = stats();
        long boxBefore = invQty("BOX-TEA", "SELLABLE");

        Map<String, Object> first = confirm(returnId, "COUNTER-A");
        String receipt1 = (String) ((Map<String, Object>) first.get("refund")).get("receiptNo");
        assertNotNull(receipt1);

        // 同一柜台重复提交、另一柜台重复提交（模拟重复回执）
        Map<String, Object> second = confirm(returnId, "COUNTER-A");
        Map<String, Object> third = confirm(returnId, "COUNTER-B");
        assertEquals(receipt1, ((Map<String, Object>) second.get("refund")).get("receiptNo"));
        assertEquals(receipt1, ((Map<String, Object>) third.get("refund")).get("receiptNo"));

        Map<String, Number> after = stats();
        assertEquals(num(before, "refunds") + 1, num(after, "refunds"), "三次确认只能产生一笔退款");
        assertEquals(num(before, "paymentTxns") + 1, num(after, "paymentTxns"), "支付网关只执行一次退款");
        assertEquals(boxBefore + 1, invQty("BOX-TEA", "SELLABLE"), "库存只调整一次");
        assertEquals(1, txnCountFor(returnId));
    }

    // ---------- 4. 两个柜台同时确认：一笔退款、一次库存调整 ----------

    @Test
    void concurrentConfirmFromTwoCountersProducesSingleRefund() throws Exception {
        long itemId = itemIdOf("ORD-1004");
        long returnId = createReturn(itemId, "it-race-" + itemId);
        // 芒果干破损 1 件，其余完好 → 散件入库，整盒不变
        setLines(returnId, false, Map.of("MANGO", new int[]{1, 0, 1}));
        Map<String, Object> quoted = quote(returnId);
        long expectedRefund = ((Number) quoted.get("quotedAmountCents")).longValue();

        Map<String, Number> before = stats();
        long boxBefore = invQty("BOX-FRUIT", "SELLABLE");
        long mangoDamagedBefore = invQty("MANGO", "DAMAGED");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String counter = "COUNTER-" + (i % 2 + 1); // 两个柜台
            futures.add(pool.submit(() -> {
                gate.await();
                return given().contentType(ContentType.JSON)
                        .body(Map.of("counterId", counter))
                        .post("/api/returns/" + returnId + "/confirm");
            }));
        }
        gate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        Set<String> receipts = new HashSet<>();
        for (Future<Response> f : futures) {
            Response r = f.get();
            assertEquals(200, r.statusCode(), "所有柜台都应拿到有效回执: " + r.asString());
            String receipt = r.path("refund.receiptNo");
            assertNotNull(receipt);
            receipts.add(receipt);
            assertEquals(expectedRefund, ((Number) r.path("refund.amountCents")).longValue());
        }
        assertEquals(1, receipts.size(), "并发确认只能产生一张有效回执");

        Map<String, Number> after = stats();
        assertEquals(num(before, "refunds") + 1, num(after, "refunds"), "只能有一笔退款");
        assertEquals(num(before, "paymentTxns") + 1, num(after, "paymentTxns"), "支付只执行一次");
        assertEquals(4, txnCountFor(returnId), "库存流水只落一套（3 完好 + 1 破损）");
        assertEquals(boxBefore, invQty("BOX-FRUIT", "SELLABLE"), "破损礼盒不得恢复整盒");
        assertEquals(mangoDamagedBefore + 1, invQty("MANGO", "DAMAGED"));
    }

    // ---------- 5. 建单幂等：同键同单，同行唯一 ----------

    @Test
    void createReturnIsIdempotentPerKeyAndUniquePerOrderItem() {
        long itemId = itemIdOf("ORD-1005");
        String key = "it-create-" + itemId;
        long first = createReturn(itemId, key);
        long second = createReturn(itemId, key);
        assertEquals(first, second, "同一幂等键重复建单应返回同一退货单");

        given().contentType(ContentType.JSON)
                .body(Map.of("orderItemId", itemId, "counterId", "COUNTER-T", "idempotencyKey", key + "-other"))
                .post("/api/returns")
                .then().statusCode(409);
    }
}
