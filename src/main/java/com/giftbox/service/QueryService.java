package com.giftbox.service;

import com.giftbox.domain.CustomerOrder;
import com.giftbox.domain.ReturnCase;
import com.giftbox.domain.StockEntry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** 只读查询：订单调出、退货单回看、库存核对。 */
@ApplicationScoped
public class QueryService {

    @Inject
    EntityManager em;

    @Transactional
    public List<CustomerOrder> listOrders() {
        return em.createQuery("select distinct o from CustomerOrder o left join fetch o.returnCases "
                + "order by o.id desc", CustomerOrder.class).getResultList();
    }

    @Transactional
    public Optional<CustomerOrder> findOrderByCode(String code) {
        return em.createQuery("select o from CustomerOrder o where o.code = :code", CustomerOrder.class)
                .setParameter("code", code)
                .getResultList().stream().findFirst();
    }

    @Transactional
    public CustomerOrder getOrder(Long id) {
        return em.find(CustomerOrder.class, id);
    }

    @Transactional
    public List<ReturnCase> listReturnsByOrder(Long orderId) {
        return em.createQuery("select r from ReturnCase r where r.order.id = :oid order by r.id",
                        ReturnCase.class)
                .setParameter("oid", orderId)
                .getResultList();
    }

    @Transactional
    public ReturnCase getReturn(Long id) {
        return em.find(ReturnCase.class, id);
    }

    @Transactional
    public List<StockEntry> listStock() {
        return em.createQuery("select s from StockEntry s order by s.kind, s.id", StockEntry.class)
                .getResultList();
    }
}
