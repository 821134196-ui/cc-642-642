package com.giftbox.api;

import com.giftbox.domain.CustomerOrder;
import com.giftbox.service.QueryService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/** 订单调出：列出/按单号查询/详情（含应有组件与既有退货办理记录）。 */
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class OrderResource {

    @Inject
    QueryService queries;

    @Inject
    ViewMapper mapper;

    @GET
    public List<Map<String, Object>> list() {
        return queries.listOrders().stream().map(mapper::order).toList();
    }

    @GET
    @Path("/by-code/{code}")
    public Response byCode(@PathParam("code") String code) {
        return queries.findOrderByCode(code)
                .<Response>map(o -> Response.ok(mapper.order(o)).build())
                .orElseGet(() -> Response.status(404)
                        .entity(Map.of("error", "未找到订单: " + code)).build());
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        CustomerOrder o = queries.getOrder(id);
        if (o == null) {
            return Response.status(404).entity(Map.of("error", "订单不存在")).build();
        }
        Map<String, Object> view = mapper.order(o);
        view.put("returnCases", queries.listReturnsByOrder(id).stream()
                .map(r -> mapper.returnCase(r, true)).toList());
        return Response.ok(view).build();
    }
}
