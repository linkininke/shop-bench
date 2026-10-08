package com.shopbench.store;

import com.shopbench.model.AdCampaign;
import com.shopbench.model.Product;
import com.shopbench.model.StockLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ShopStore {
    private final Map<Long, Product> products = new LinkedHashMap<>();
    private final Map<Long, AdCampaign> ads = new LinkedHashMap<>();
    private final List<StockLog> logs = new ArrayList<>();
    private final Map<String, Set<Long>> purchases = new LinkedHashMap<>();
    private final Map<String, String> userNames = new LinkedHashMap<>();
    private long logSeq = 1;
    private long productSeq = 113;
    private int revenueCents;
    private int profitCents;

    public ShopStore() {
        reset();
    }

    public synchronized void reset() {
        products.clear();
        ads.clear();
        logs.clear();
        purchases.clear();
        userNames.clear();
        logSeq = 1;
        productSeq = 113;
        revenueCents = 0;
        profitCents = 0;
        seed();
    }

    private void seed() {
        add(101, "晋城手工醋", "调味", 2800, 1200, 40, 10, "三年陈酿，凉拌和饺子都合适");
        add(102, "太行山蜂蜜", "滋补", 6800, 3000, 25, 8, "槐花蜜，结晶是正常现象");
        add(103, "陵川党参", "滋补", 5200, 2400, 15, 5, "切片，适合煲汤");
        add(104, "高平丝绸围巾", "服饰", 12800, 7000, 6, 3, "真丝手感，秋冬送礼");
        add(105, "泽州铁器小刀", "日用", 4500, 2000, 2, 5, "手工锻打，库存偏低");
        add(106, "阳城陶瓷杯", "日用", 3900, 1500, 30, 8, "粗陶茶杯");
        add(107, "皇城相府笔记本", "文创", 2600, 800, 50, 10, "景点文创本");
        add(108, "本地羊肉汤料包", "食品", 3200, 1400, 20, 6, "香料包，不含肉");
        add(109, "苹果醋饮料", "饮品", 900, 300, 60, 12, "无糖果醋饮料，冰镇更好");
        add(110, "蜂蜜柚子茶", "饮品", 2400, 900, 18, 6, "蜂蜜和柚子皮熬制");
        add(111, "党参黄芪茶", "滋补", 3600, 1500, 12, 4, "党参黄芪组合茶包");
        add(112, "丝绸礼盒", "服饰", 19800, 11000, 4, 2, "围巾加礼盒包装");

        userNames.put("u-li", "李强");
        userNames.put("u-wang", "王敏");
        userNames.put("u-zhao", "赵磊");
        buy("u-li", 101);
        buy("u-li", 109);
        buy("u-wang", 101);
        buy("u-wang", 109);
        buy("u-wang", 102);
        buy("u-zhao", 104);
        buy("u-zhao", 107);

        ads.put(201L, new AdCampaign(201, 102, List.of("蜂蜜", "蜜", "槐花"), 80, 20000, 8, 100));
        ads.put(202L, new AdCampaign(202, 109, List.of("醋", "苹果", "饮料"), 120, 20000, 1, 80));
        ads.put(203L, new AdCampaign(203, 101, List.of("醋", "手工", "陈醋"), 30, 20000, 2, 40));
    }

    private void add(long id, String name, String category, int price, int cost, int stock, int warn, String desc) {
        products.put(id, new Product(id, name, category, price, cost, stock, warn, desc));
    }

    public synchronized Product create(String name, String category, int priceCents, int costCents, int stock, int warnBelow, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("商品名不能为空");
        }
        if (priceCents <= 0 || costCents < 0 || stock < 0 || warnBelow < 0) {
            throw new IllegalArgumentException("价格、成本和库存要是有效数字");
        }
        if (costCents >= priceCents) {
            throw new IllegalArgumentException("成本必须低于售价，否则没有毛利");
        }
        Product product = new Product(productSeq++, name.trim(), category, priceCents, costCents, stock, warnBelow, description);
        products.put(product.id(), product);
        return product;
    }

    public synchronized void addSaleMoney(int revenue, int profit) {
        revenueCents += revenue;
        profitCents += profit;
    }

    public synchronized int revenueCents() { return revenueCents; }
    public synchronized int profitCents() { return profitCents; }

    private void buy(String userId, long skuId) {
        purchases.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add(skuId);
    }

    public synchronized List<Product> products() {
        return List.copyOf(products.values());
    }

    public synchronized Product product(long id) {
        Product product = products.get(id);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在：" + id);
        }
        return product;
    }

    public synchronized List<AdCampaign> ads() {
        return List.copyOf(ads.values());
    }

    public synchronized AdCampaign ad(long id) {
        AdCampaign ad = ads.get(id);
        if (ad == null) {
            throw new IllegalArgumentException("广告不存在：" + id);
        }
        return ad;
    }

    public synchronized void addPurchase(String userId, long skuId) {
        if (userId != null && !userId.isBlank()) {
            buy(userId, skuId);
        }
    }

    public synchronized Map<String, Set<Long>> purchases() {
        Map<String, Set<Long>> copy = new LinkedHashMap<>();
        purchases.forEach((user, items) -> copy.put(user, Set.copyOf(items)));
        return copy;
    }

    public synchronized Map<String, String> userNames() {
        return Map.copyOf(userNames);
    }

    public synchronized void addLog(long skuId, String skuName, int delta, String type, String note) {
        logs.add(new StockLog(logSeq++, skuId, skuName, delta, type, note, System.currentTimeMillis()));
    }

    public synchronized List<StockLog> logs() {
        return logs.reversed();
    }
}
