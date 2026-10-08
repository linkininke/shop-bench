package com.shopbench.erp;

import com.shopbench.model.Product;
import com.shopbench.model.StockLog;
import com.shopbench.search.SearchService;
import com.shopbench.store.ShopStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {
    private final ShopStore store;
    private final SearchService searchService;

    public InventoryService(ShopStore store, SearchService searchService) {
        this.store = store;
        this.searchService = searchService;
    }

    public synchronized StockLog purchaseIn(long skuId, int qty, String note) {
        requireQty(qty);
        Product product = store.product(skuId);
        product.addStock(qty);
        store.addLog(skuId, product.name(), qty, "PURCHASE_IN", blankTo(note, "采购入库"));
        return store.logs().getFirst();
    }

    public synchronized StockLog saleOut(long skuId, int qty, String userId) {
        requireQty(qty);
        Product product = store.product(skuId);
        product.removeStock(qty);
        store.addPurchase(userId, skuId);
        int revenue = product.priceCents() * qty;
        int profit = product.marginCents() * qty;
        store.addSaleMoney(revenue, profit);
        String who = userId == null || userId.isBlank() ? "散客" : store.userNames().getOrDefault(userId, userId);
        store.addLog(skuId, product.name(), -qty, "SALE_OUT", who + " 销售出库，金额 " + yuan(revenue) + " 元");
        return store.logs().getFirst();
    }

    public synchronized StockLog adjust(long skuId, int counted) {
        if (counted < 0) {
            throw new IllegalArgumentException("盘点数量不能为负");
        }
        Product product = store.product(skuId);
        int delta = counted - product.stock();
        if (delta == 0) {
            throw new IllegalArgumentException("数量没有变化");
        }
        product.setStock(counted);
        store.addLog(skuId, product.name(), delta, "ADJUST", "盘点改为 " + counted);
        return store.logs().getFirst();
    }

    public synchronized Product create(String name, String category, int priceCents, int costCents, int stock, int warnBelow, String description) {
        Product product = store.create(name, category, priceCents, costCents, stock, warnBelow, description == null ? "" : description);
        searchService.rebuild();
        if (stock > 0) {
            store.addLog(product.id(), product.name(), stock, "PURCHASE_IN", "新品建档入库");
        }
        return product;
    }

    public synchronized List<Product> alerts() {
        return store.products().stream().filter(Product::lowStock).toList();
    }

    public synchronized void reset() {
        store.reset();
        searchService.rebuild();
    }

    private static void requireQty(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String yuan(int cents) {
        return String.format(java.util.Locale.CHINA, "%.2f", cents / 100.0);
    }
}
