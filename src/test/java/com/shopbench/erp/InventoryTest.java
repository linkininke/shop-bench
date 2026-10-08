package com.shopbench.erp;

import com.shopbench.search.SearchService;
import com.shopbench.store.ShopStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTest {
    @Test
    void cannotOversellAndPurchaseRestocks() {
        ShopStore store = new ShopStore();
        InventoryService erp = new InventoryService(store, new SearchService(store));
        assertTrue(store.product(105).lowStock());
        assertThrows(IllegalArgumentException.class, () -> erp.saleOut(105, 3, "u-li"));
        assertEquals(2, store.product(105).stock());
        erp.purchaseIn(105, 10, "补货");
        assertEquals(12, store.product(105).stock());
        erp.saleOut(105, 2, "u-li");
        assertEquals(10, store.product(105).stock());
        assertTrue(store.purchases().get("u-li").contains(105L));
        assertEquals(9000, store.revenueCents());
        assertEquals(5000, store.profitCents());
    }

    @Test
    void newProductCanBeSearchedAndStockCanBeCounted() {
        ShopStore store = new ShopStore();
        SearchService search = new SearchService(store);
        InventoryService erp = new InventoryService(store, search);
        var created = erp.create("山楂条", "食品", 1500, 600, 20, 5, "酸甜零食");
        assertTrue(search.search("山楂", 5).containsKey(created.id()));
        erp.adjust(created.id(), 7);
        assertEquals(7, store.product(created.id()).stock());
    }
}
