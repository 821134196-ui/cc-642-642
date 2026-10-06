package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.api.dto.Dtos.OrderDetailDto;
import com.departmentstore.giftbox.api.dto.Dtos.OrderItemDto;
import com.departmentstore.giftbox.api.dto.Dtos.OrderSummaryDto;
import com.departmentstore.giftbox.model.CustomerOrder;
import com.departmentstore.giftbox.model.OrderItem;
import com.departmentstore.giftbox.model.ReturnRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 订单查询：调出原订单，连同其退货处理过程 */
@Path("/api/orders")
public class OrderResource {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GET
    @Transactional
    public List<OrderSummaryDto> list() {
        return CustomerOrder.<CustomerOrder>listAll().stream()
                .map(o -> new OrderSummaryDto(o.orderNo, o.customerName, o.createdAt.format(FMT),
                        o.items.size(),
                        o.items.stream().mapToLong(i -> i.comboPriceCents * i.qty).sum()))
                .toList();
    }

    @GET
    @Path("/{orderNo}")
    @Transactional
    public OrderDetailDto detail(@PathParam("orderNo") String orderNo) {
        CustomerOrder order = CustomerOrder.findByOrderNo(orderNo);
        if (order == null) {
            throw new NotFoundException("订单不存在: " + orderNo);
        }
        List<OrderItemDto> items = order.items.stream().map(item -> {
            ReturnRequest rr = ReturnRequest.findByOrderItem(item);
            return Mappers.toItemDto(item, rr == null ? null : rr.id);
        }).toList();
        return Mappers.toDto(order, items);
    }
}
