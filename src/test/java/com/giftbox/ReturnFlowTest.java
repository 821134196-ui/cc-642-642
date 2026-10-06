package com.giftbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：
 *  1) 破损+缺失 → 只散件入库、不出整盒，退款+扣减==组合价；
 *  2) 全部完好 → 整盒复原；
 *  3) 两柜台并发办理同一订单 → 恰一笔有效退款、一次库存调整；
 *  4) 重复办理与旧回执重放 → 不二次退款、不增加库存。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReturnFlowTest {

    private static final String BASE = "http://localhost:8080";
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private JsonNode req(String method, String path, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path));
        if (body != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException(method + " " + path + " -> " + res.statusCode() + " " + res.body());
        }
        return res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
    }

    private JsonNode findOrder(String code) throws Exception {
        return req("GET", "/api/orders/by-code/" + code, null);
    }

    private long createReturn(long orderId, String counter) throws Exception {
        JsonNode rc = req("POST", "/api/returns", Map.of("orderId", orderId, "counter", counter));
        return rc.get("id").asLong();
    }

    private JsonNode setLine(long rid, JsonNode line, int intact, int damaged, int missing) throws Exception {
        return req("PUT", "/api/returns/" + rid + "/lines/" + line.get("id").asLong(),
                Map.of("intactQty", intact, "damagedQty", damaged, "missingQty", missing,
                        "note", "IT登记"));
    }

    private List<JsonNode> stockFor(String orderCode) throws Exception {
        JsonNode stock = req("GET", "/api/stock", null);
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : stock.get("entries")) {
            if (orderCode.equals(e.path("sourceOrderCode").asText(null))) {
                out.add(e);
            }
        }
        return out;
    }

    @Test
    @Order(1)
    void 破损与缺失时只散件入库且金额勾稽() throws Exception {
        JsonNode order = findOrder("SO-1003"); // 茶点礼盒 300
        long rid = createReturn(order.get("id").asLong(), "A柜");
        JsonNode rc = req("GET", "/api/returns/" + rid, null);

        // 曲奇破损 1、赠品蜂蜜缺失 1；其余完好
        for (JsonNode line : rc.get("lines")) {
            String name = line.get("component").get("name").asText();
            int expected = line.get("expectedQty").asInt();
            if (name.contains("曲奇")) {
                rc = setLine(rid, line, 0, 1, 0);
            } else if (name.contains("蜂蜜")) {
                rc = setLine(rid, line, 0, 0, 1);
            }
        }

        JsonNode quote = rc.get("quote");
        assertTrue(quote.get("balanced").asBoolean(), "退款+扣减必须等于组合价");
        assertEquals("300.00", quote.get("comboPriceYuan").asText());
        assertTrue(Double.parseDouble(quote.get("deductionYuan").asText()) > 0, "应有扣减");

        // 扣减项中赠品缺失与商品破损分别存在
        boolean giftMissing = false, productDamaged = false;
        for (JsonNode line : rc.get("lines")) {
            if (line.hasNonNull("charges")) {
                for (JsonNode ch : line.get("charges")) {
                    if (ch.get("title").asText().equals("赠品缺失")) giftMissing = true;
                    if (ch.get("title").asText().equals("商品破损")) productDamaged = true;
                    assertTrue(ch.get("basis").asText().contains("组合价分摊"));
                }
            }
        }
        assertTrue(giftMissing, "必须展示赠品缺失报价与依据");
        assertTrue(productDamaged, "必须展示商品破损报价与依据");

        req("POST", "/api/returns/" + rid + "/confirm", null);
        JsonNode done = req("POST", "/api/returns/" + rid + "/complete", null);
        assertEquals("COMPLETED", done.get("status").asText());
        assertFalse(done.get("boxRestored").asBoolean(), "有破损/缺失不能恢复整盒");

        List<JsonNode> entries = stockFor("SO-1003");
        for (JsonNode e : entries) {
            assertEquals("LOOSE", e.get("kind").asText(), "库存中不能出现未复原的整盒");
        }
        // 完好件：红茶1、礼袋1、茶勺1 → 3 个散件批次；曲奇破损、蜂蜜缺失不得入库
        assertEquals(3, entries.size(), "只有三个完好组件入库");
        List<String> looseNames = entries.stream().map(e -> e.get("name").asText()).toList();
        assertTrue(looseNames.contains("精选红茶 100g"));
        assertTrue(looseNames.contains("定制礼袋"));
        assertTrue(looseNames.contains("木质茶勺（赠品）"));
        assertFalse(looseNames.stream().anyMatch(n -> n.contains("曲奇")));
        assertFalse(looseNames.stream().anyMatch(n -> n.contains("蜂蜜")));

        // 金额落库与报价一致
        assertEquals(quote.get("refundYuan").asText(), done.get("refundYuan").asText());
        assertEquals(quote.get("deductionYuan").asText(), done.get("deductionYuan").asText());
    }

    @Test
    @Order(2)
    void 全部完好时恢复整盒() throws Exception {
        JsonNode order = findOrder("SO-1001"); // 香氛礼盒 500，默认全部完好
        long rid = createReturn(order.get("id").asLong(), "A柜");
        JsonNode done = req("POST", "/api/returns/" + rid + "/complete", null);

        assertEquals("COMPLETED", done.get("status").asText());
        assertTrue(done.get("boxRestored").asBoolean());
        assertEquals("500.00", done.get("refundYuan").asText());
        assertEquals("0.00", done.get("deductionYuan").asText());

        List<JsonNode> entries = stockFor("SO-1001");
        assertEquals(1, entries.size());
        assertEquals("BOX", entries.get(0).get("kind").asText());
        assertEquals(1, entries.get(0).get("quantity").asInt());
    }

    @Test
    @Order(3)
    void 两柜台并发只产生一笔退款一次入库() throws Exception {
        JsonNode order = findOrder("SO-1004"); // 468 成交
        long ridA = createReturn(order.get("id").asLong(), "A柜");
        long ridB = createReturn(order.get("id").asLong(), "B柜");

        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Future<JsonNode> fa = pool.submit(() -> {
            start.await();
            return req("POST", "/api/returns/" + ridA + "/complete", null);
        });
        Future<JsonNode> fb = pool.submit(() -> {
            start.await();
            return req("POST", "/api/returns/" + ridB + "/complete", null);
        });
        start.countDown();
        JsonNode ra = fa.get();
        JsonNode rb = fb.get();
        pool.shutdown();

        boolean aWin = !ra.get("rejected").asBoolean();
        boolean bWin = !rb.get("rejected").asBoolean();
        assertTrue(aWin ^ bWin, "必须恰好一个柜台办理成功");
        JsonNode winner = aWin ? ra : rb;
        JsonNode loser = aWin ? rb : ra;
        assertEquals("COMPLETED", winner.get("status").asText());
        assertEquals("REJECTED", loser.get("status").asText());
        assertEquals("SUCCESS", winner.get("result").asText());
        assertEquals("DUPLICATE_REJECTED", loser.get("result").asText());
        assertNotNull(winner.get("resultReceipt").asText(null));

        // 成功退款金额 = 468 - 0（全完好）
        assertEquals("468.00", winner.get("refundYuan").asText());

        // 整盒库存恰好增加 1，且没有来自败方的入库
        List<JsonNode> entries = stockFor("SO-1004");
        assertEquals(1, entries.size(), "只允许一次库存调整");
        assertEquals("BOX", entries.get(0).get("kind").asText());
        assertEquals(1, entries.get(0).get("quantity").asInt());

        // 订单级唯一退款回执数（回看整单）
        JsonNode fullOrder = req("GET", "/api/orders/" + order.get("id").asLong(), null);
        int success = 0, rejected = 0;
        for (JsonNode c : fullOrder.get("returnCases")) {
            for (JsonNode r : c.get("refunds")) {
                if (r.get("result").asText().equals("SUCCESS")) success++;
                if (r.get("result").asText().equals("DUPLICATE_REJECTED")) rejected++;
            }
        }
        assertEquals(1, success, "每订单只能有一笔有效退款");
        assertEquals(1, rejected, "败方应留一条拒绝审计记录");
    }

    @Test
    @Order(4)
    void 重复办理与回执重放不二次退款不增加库存() throws Exception {
        JsonNode order = req("GET", "/api/orders/by-code/SO-1004", null);
        JsonNode full = req("GET", "/api/orders/" + order.get("id").asLong(), null);

        long winnerId = -1, loserId = -1;
        String receipt = null;
        for (JsonNode c : full.get("returnCases")) {
            if (c.get("status").asText().equals("COMPLETED")) {
                winnerId = c.get("id").asLong();
                for (JsonNode r : c.get("refunds")) {
                    if (r.get("result").asText().equals("SUCCESS")) {
                        receipt = r.get("receiptNo").asText();
                    }
                }
            } else if (c.get("status").asText().equals("REJECTED")) {
                loserId = c.get("id").asLong();
            }
        }
        assertTrue(winnerId > 0 && loserId > 0 && receipt != null);

        int stockBefore = stockFor("SO-1004").size();

        // 赢家重复办理：幂等回显
        JsonNode again = req("POST", "/api/returns/" + winnerId + "/complete", null);
        assertTrue(again.get("alreadyDone").asBoolean());
        assertEquals(stockBefore, stockFor("SO-1004").size(), "重复办理不得增加库存");

        // 输家再办理：仍拒绝
        JsonNode loserAgain = req("POST", "/api/returns/" + loserId + "/complete", null);
        assertTrue(loserAgain.get("rejected").asBoolean());
        assertEquals(stockBefore, stockFor("SO-1004").size());

        // 拿旧回执再退：仅回显原记录
        JsonNode replay = req("POST", "/api/returns/replay-receipt/" + receipt, null);
        assertEquals("SUCCESS", replay.get("result").asText());
        assertEquals(receipt, replay.get("receiptNo").asText());
        assertEquals("468.00", replay.get("amountYuan").asText());
        assertEquals(stockBefore, stockFor("SO-1004").size(), "回执重放不得增加库存");

        // 全库整盒中不得有任何“破损/缺失单”伪装成的整盒（SO-1003 无 BOX）
        for (JsonNode e : stockFor("SO-1003")) {
            assertFalse("BOX".equals(e.get("kind").asText()));
        }
    }
}
