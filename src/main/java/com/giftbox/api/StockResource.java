package com.giftbox.api;

import com.giftbox.service.QueryService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 库存核对：区分整盒与散件批次。 */
@Path("/api/stock")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class StockResource {

    @Inject
    QueryService queries;

    @Inject
    ViewMapper mapper;

    public record StockSummary(long boxKinds, long boxTotal, long looseBatches, long looseTotal,
                               List<Map<String, Object>> entries) {
    }

    @GET
    public StockSummary list() {
        var entries = queries.listStock();
        long boxTotal = entries.stream()
                .filter(e -> e.getKind().name().equals("BOX"))
                .mapToLong(com.giftbox.domain.StockEntry::getQuantity).sum();
        long looseTotal = entries.stream()
                .filter(e -> e.getKind().name().equals("LOOSE"))
                .mapToLong(com.giftbox.domain.StockEntry::getQuantity).sum();
        long boxKinds = entries.stream()
                .filter(e -> e.getKind().name().equals("BOX"))
                .map(e -> e.getGiftBox().getCode()).distinct().count();
        long looseBatches = entries.stream().filter(e -> e.getKind().name().equals("LOOSE")).count();
        return new StockSummary(boxKinds, boxTotal, looseBatches, looseTotal,
                entries.stream().map(mapper::stock).toList());
    }
}
