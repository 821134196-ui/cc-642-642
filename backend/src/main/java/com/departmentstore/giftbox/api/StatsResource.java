package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.model.InventoryTxn;
import com.departmentstore.giftbox.model.PaymentRefundTxn;
import com.departmentstore.giftbox.model.Refund;
import com.departmentstore.giftbox.model.ReturnRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** 全局计数：用于核对“一笔退货只产生一笔退款、一次支付、一套库存流水” */
@Path("/api/stats")
public class StatsResource {

    public record Stats(long returnRequests, long refunds, long paymentTxns, long inventoryTxns) {
    }

    @GET
    @Transactional
    public Stats stats() {
        return new Stats(ReturnRequest.count(), Refund.count(),
                PaymentRefundTxn.count(), InventoryTxn.count());
    }
}
