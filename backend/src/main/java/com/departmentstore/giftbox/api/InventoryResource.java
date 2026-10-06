package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.api.dto.Dtos.InventoryDto;
import com.departmentstore.giftbox.model.GiftBox;
import com.departmentstore.giftbox.model.Inventory;
import com.departmentstore.giftbox.model.InventoryTxn;
import com.departmentstore.giftbox.model.Product;
import com.departmentstore.giftbox.model.SkuKind;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Comparator;
import java.util.List;

/** 库存查询：整盒与散件分列，可售/破损分状态 */
@Path("/api/inventory")
public class InventoryResource {

    public record InventoryView(List<InventoryDto> rows, List<TxnDto> recentTxns) {
    }

    public record TxnDto(long id, String sku, String skuKind, String state, long delta,
                         String reason, Long returnRequestId, String createdAt) {
    }

    @GET
    @Transactional
    public InventoryView view() {
        List<InventoryDto> rows = Inventory.<Inventory>listAll().stream()
                .sorted(Comparator.comparing((Inventory i) -> i.skuKind).thenComparing(i -> i.sku)
                        .thenComparing(i -> i.state))
                .map(inv -> new InventoryDto(inv.sku, displayName(inv), inv.skuKind.name(), inv.state.name(), inv.qty))
                .toList();
        List<TxnDto> txns = InventoryTxn.<InventoryTxn>listAll().stream()
                .sorted(Comparator.comparing((InventoryTxn t) -> t.id).reversed())
                .limit(50)
                .map(t -> new TxnDto(t.id, t.sku, t.skuKind.name(), t.state.name(), t.delta,
                        t.reason, t.returnRequestId,
                        t.createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .toList();
        return new InventoryView(rows, txns);
    }

    private static String displayName(Inventory inv) {
        if (inv.skuKind == SkuKind.BOX) {
            GiftBox box = GiftBox.findBySku(inv.sku);
            return box != null ? box.name + "（整盒）" : inv.sku;
        }
        Product p = Product.findBySku(inv.sku);
        return p != null ? p.name + "（散件）" : inv.sku;
    }
}
