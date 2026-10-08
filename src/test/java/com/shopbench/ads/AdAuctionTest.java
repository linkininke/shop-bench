package com.shopbench.ads;

import com.shopbench.model.AdCampaign;
import com.shopbench.store.ShopStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdAuctionTest {
    @Test
    void higherRankScoreWinsHoneySlot() {
        ShopStore store = new ShopStore();
        AdService ads = new AdService(store);
        var served = ads.pick("蜂蜜");
        assertTrue(served.isPresent());
        assertEquals(102L, served.get().product().id());
    }

    @Test
    void raisingBidChangesWinner() {
        ShopStore store = new ShopStore();
        AdService ads = new AdService(store);
        assertEquals(109L, ads.pick("醋").orElseThrow().product().id());
        ads.updateBid(203, 800);
        assertEquals(101L, ads.pick("醋").orElseThrow().product().id());
    }

    @Test
    void clickChargesBidAndStopsWhenBudgetGone() {
        AdCampaign ad = new AdCampaign(1, 101, java.util.List.of("醋"), 100, 250, 0, 0);
        ad.chargeClick();
        assertEquals(100, ad.spentCents());
        ad.chargeClick();
        assertEquals(200, ad.spentCents());
        assertFalse(ad.canServe());
    }

    @Test
    void vinegarAuctionListsAppleAheadOfHandmade() {
        var board = new AdService(new ShopStore()).explain("醋");
        assertEquals(109L, board.get(0).campaign().skuId());
        assertTrue(board.stream().anyMatch(row -> row.campaign().skuId() == 101L && row.matched()));
        assertTrue(board.stream().anyMatch(row -> row.campaign().skuId() == 102L && !row.matched()));
    }
}
