package com.departmentstore.giftbox.service;

import com.departmentstore.giftbox.model.Inventory;
import com.departmentstore.giftbox.model.InventoryState;
import com.departmentstore.giftbox.model.InventoryTxn;
import com.departmentstore.giftbox.model.SkuKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

/**
 * 库存服务。所有调整都写流水；同一退货单对同一 (sku, 形态, 状态)
 * 的调整只生效一次（先查流水，再有数据库唯一约束兜底）。
 */
@ApplicationScoped
public class InventoryService {

    @Transactional
    public void adjust(SkuKind kind, String sku, InventoryState state, long delta,
                       Long returnRequestId, String reason) {
        if (delta == 0) {
            return;
        }
        if (returnRequestId != null
                && InventoryTxn.existsForReturn(returnRequestId, sku, kind, state)) {
            return; // 幂等：该退货单已调整过这一行
        }
        Inventory row = Inventory.findRow(sku, kind, state);
        if (row == null) {
            row = new Inventory();
            row.sku = sku;
            row.skuKind = kind;
            row.state = state;
            row.qty = 0;
        }
        row.qty += delta;
        row.persist();

        InventoryTxn txn = new InventoryTxn();
        txn.returnRequestId = returnRequestId;
        txn.sku = sku;
        txn.skuKind = kind;
        txn.state = state;
        txn.delta = delta;
        txn.reason = reason;
        txn.createdAt = LocalDateTime.now();
        txn.persist();
    }
}
