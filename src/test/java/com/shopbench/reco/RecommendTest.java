package com.shopbench.reco;

import com.shopbench.store.ShopStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendTest {
    @Test
    void liGetsHoneyBecauseWangBoughtBoth() {
        RecommendService reco = new RecommendService(new ShopStore());
        var names = reco.recommend("u-li", 4).stream().map(s -> s.product().name()).toList();
        assertTrue(names.contains("太行山蜂蜜"));
    }

    @Test
    void contentModeUsesCategoryInsteadOfCoBuyers() {
        RecommendService reco = new RecommendService(new ShopStore());
        var names = reco.recommend("u-zhao", 4, "content").stream().map(s -> s.product().name()).toList();
        assertTrue(names.contains("丝绸礼盒"));
        assertTrue(!names.contains("太行山蜂蜜"));
    }
}
