package com.departmentstore.giftbox.service;

import com.departmentstore.giftbox.model.CustomerOrder;
import com.departmentstore.giftbox.model.GiftBox;
import com.departmentstore.giftbox.model.GiftBoxComponent;
import com.departmentstore.giftbox.model.Inventory;
import com.departmentstore.giftbox.model.InventoryState;
import com.departmentstore.giftbox.model.OrderItem;
import com.departmentstore.giftbox.model.Product;
import com.departmentstore.giftbox.model.ProductKind;
import com.departmentstore.giftbox.model.SkuKind;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

/** 启动时初始化演示数据（仅当数据库为空） */
@ApplicationScoped
public class SeedService {

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (Product.count() > 0) {
            return;
        }

        // ---- 组件目录（零售标价，单位：分） ----
        Product teaRed = product("TEA-RED", "滇红红茶罐", ProductKind.MAIN, 26800);
        Product teaGreen = product("TEA-GREEN", "龙井绿茶罐", ProductKind.MAIN, 22800);
        Product teapot = product("TEAPOT", "紫砂茶壶", ProductKind.ACCESSORY, 15800);
        Product teacup = product("TEACUP", "紫砂茶杯", ProductKind.ACCESSORY, 5800);
        Product card = product("CARD", "祝福贺卡", ProductKind.GIFT, 2000);
        Product sample = product("SAMPLE", "小样茶包", ProductKind.GIFT, 3000);
        Product mango = product("MANGO", "芒果干", ProductKind.MAIN, 5900);
        Product strawberry = product("STRAW", "草莓干", ProductKind.MAIN, 4900);
        Product nuts = product("NUTS", "坚果包", ProductKind.ACCESSORY, 3900);
        Product trial = product("TRIAL", "试吃装", ProductKind.GIFT, 1500);

        // ---- 礼盒 ----
        GiftBox teaBox = giftBox("BOX-TEA", "锦绣茶礼", 58800);
        component(teaBox, teaRed, 1);
        component(teaBox, teaGreen, 1);
        component(teaBox, teapot, 1);
        component(teaBox, teacup, 2);
        component(teaBox, card, 1);
        component(teaBox, sample, 1);

        GiftBox fruitBox = giftBox("BOX-FRUIT", "缤纷果干礼盒", 19900);
        component(fruitBox, mango, 1);
        component(fruitBox, strawberry, 1);
        component(fruitBox, nuts, 1);
        component(fruitBox, trial, 1);

        // ---- 期初库存 ----
        inventory("BOX-TEA", SkuKind.BOX, InventoryState.SELLABLE, 10);
        inventory("BOX-FRUIT", SkuKind.BOX, InventoryState.SELLABLE, 20);
        inventory("TEACUP", SkuKind.COMPONENT, InventoryState.SELLABLE, 5);
        inventory("MANGO", SkuKind.COMPONENT, InventoryState.SELLABLE, 8);
        inventory("TEAPOT", SkuKind.COMPONENT, InventoryState.DAMAGED, 1);

        // ---- 订单 ----
        order("ORD-1001", "张伟", "138****0001", teaBox, 1, 58800);
        order("ORD-1002", "李娜", "138****0002", fruitBox, 1, 19900);
        order("ORD-1003", "王强", "138****0003", teaBox, 1, 58800);
        order("ORD-1004", "赵敏", "138****0004", fruitBox, 1, 19900);
        order("ORD-1005", "陈晨", "138****0005", fruitBox, 1, 19900);
    }

    private Product product(String sku, String name, ProductKind kind, long listPriceCents) {
        Product p = new Product();
        p.sku = sku;
        p.name = name;
        p.kind = kind;
        p.listPriceCents = listPriceCents;
        p.persist();
        return p;
    }

    private GiftBox giftBox(String sku, String name, long comboPriceCents) {
        GiftBox box = new GiftBox();
        box.sku = sku;
        box.name = name;
        box.comboPriceCents = comboPriceCents;
        box.persist();
        return box;
    }

    private void component(GiftBox box, Product product, int qty) {
        GiftBoxComponent c = new GiftBoxComponent();
        c.giftBox = box;
        c.product = product;
        c.qty = qty;
        c.persist();
        box.components.add(c);
    }

    private void inventory(String sku, SkuKind kind, InventoryState state, long qty) {
        Inventory inv = new Inventory();
        inv.sku = sku;
        inv.skuKind = kind;
        inv.state = state;
        inv.qty = qty;
        inv.persist();
    }

    private void order(String orderNo, String customer, String phone,
                       GiftBox box, int qty, long comboPriceCents) {
        CustomerOrder order = new CustomerOrder();
        order.orderNo = orderNo;
        order.customerName = customer;
        order.customerPhone = phone;
        order.createdAt = LocalDateTime.now().minusDays(3);
        order.persist();

        OrderItem item = new OrderItem();
        item.order = order;
        item.giftBox = box;
        item.qty = qty;
        item.comboPriceCents = comboPriceCents;
        item.persist();
        order.items.add(item);
    }
}
