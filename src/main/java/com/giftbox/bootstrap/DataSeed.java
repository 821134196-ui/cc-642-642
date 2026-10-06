package com.giftbox.bootstrap;

import com.giftbox.domain.ComponentType;
import com.giftbox.domain.CustomerOrder;
import com.giftbox.domain.GiftBox;
import com.giftbox.domain.StockEntry;
import com.giftbox.support.Money;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

/**
 * 首次启动写入演示数据：礼盒模板（含商品/配件/赠品与参考报价）、
 * 几张原订单（其中 SO-1002 成交价与模板组合价不同，验证退款按售出快照分摊）、
 * 以及期初库存。数据库已有数据时不重复写入。
 */
@ApplicationScoped
public class DataSeed {

    @Inject
    EntityManager em;

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        Long count = em.createQuery("select count(b) from GiftBox b", Long.class).getSingleResult();
        if (count != null && count > 0) {
            return;
        }

        // 礼盒一：星语香氛礼盒，模板组合价 ¥500
        GiftBox perfume = new GiftBox("HB-PERFUME", "星语香氛礼盒", Money.yuan("500.00"));
        perfume.addComponent(ComponentType.PRODUCT, "星语淡香水 50ml", Money.yuan("380.00"), 1, 1);
        perfume.addComponent(ComponentType.PRODUCT, "丝绒口红 #410", Money.yuan("180.00"), 1, 2);
        perfume.addComponent(ComponentType.ACCESSORY, "精装礼盒（含丝带）", Money.yuan("30.00"), 1, 3);
        perfume.addComponent(ComponentType.ACCESSORY, "烫金贺卡", Money.yuan("10.00"), 1, 4);
        perfume.addComponent(ComponentType.GIFT, "香薰蜡烛（赠品）", Money.yuan("60.00"), 1, 5);
        perfume.addComponent(ComponentType.GIFT, "香水小样（赠品）", Money.yuan("20.00"), 2, 6);
        em.persist(perfume);

        // 礼盒二：甄选茶点礼盒，模板组合价 ¥300
        GiftBox tea = new GiftBox("HB-TEA", "甄选茶点礼盒", Money.yuan("300.00"));
        tea.addComponent(ComponentType.PRODUCT, "精选红茶 100g", Money.yuan("150.00"), 1, 1);
        tea.addComponent(ComponentType.PRODUCT, "手工曲奇铁盒", Money.yuan("120.00"), 1, 2);
        tea.addComponent(ComponentType.ACCESSORY, "定制礼袋", Money.yuan("15.00"), 1, 3);
        tea.addComponent(ComponentType.GIFT, "洋槐蜂蜜（赠品）", Money.yuan("30.00"), 1, 4);
        tea.addComponent(ComponentType.GIFT, "木质茶勺（赠品）", Money.yuan("15.00"), 1, 5);
        em.persist(tea);

        em.flush();

        LocalDateTime base = LocalDateTime.now().minusDays(3);
        // SO-1001 按模板价成交
        em.persist(new CustomerOrder("SO-1001", "张敏", "A柜", perfume,
                perfume.getComboPriceCents(), base.minusHours(2)));
        // SO-1002 会员优惠成交 ¥480 —— 退款必须按 480 快照而非模板 500 分摊
        em.persist(new CustomerOrder("SO-1002", "李华", "B柜", perfume,
                Money.yuan("480.00"), base.minusHours(5)));
        // SO-1003 茶点礼盒
        em.persist(new CustomerOrder("SO-1003", "王芳", "A柜", tea,
                tea.getComboPriceCents(), base.minusDays(1)));
        // SO-1004 再一张香氛礼盒订单，供两个柜台并发演练
        em.persist(new CustomerOrder("SO-1004", "赵磊", "B柜", perfume,
                Money.yuan("468.00"), base.minusHours(8)));

        // 期初库存（整盒）
        em.persist(StockEntry.box(perfume, 8, "INIT", null, base.minusDays(30)));
        em.persist(StockEntry.box(tea, 5, "INIT", null, base.minusDays(30)));
        // 期初散件库存（历史退货拆散形成）
        em.persist(StockEntry.loose(
                tea.getComponents().stream()
                        .filter(c -> c.getName().contains("曲奇")).findFirst().orElseThrow(),
                2, "INIT", null, base.minusDays(20)));
    }
}
