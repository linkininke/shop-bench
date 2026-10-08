package com.shopbench.web;

import com.shopbench.ads.AdService;
import com.shopbench.erp.InventoryService;
import com.shopbench.media.FfmpegRecipes;
import com.shopbench.media.WavProcessor;
import com.shopbench.model.AdCampaign;
import com.shopbench.model.Product;
import com.shopbench.reco.RecommendService;
import com.shopbench.search.SearchService;
import com.shopbench.store.ShopStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class ApiController {
    private final ShopStore store;
    private final SearchService searchService;
    private final RecommendService recommendService;
    private final AdService adService;
    private final InventoryService inventoryService;

    public ApiController(ShopStore store, SearchService searchService, RecommendService recommendService,
                         AdService adService, InventoryService inventoryService) {
        this.store = store;
        this.searchService = searchService;
        this.recommendService = recommendService;
        this.adService = adService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/bootstrap")
    public Map<String, Object> bootstrap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("users", store.userNames());
        body.put("products", store.products().stream().map(this::productView).toList());
        body.put("ads", store.ads().stream().map(this::adView).toList());
        body.put("alerts", inventoryService.alerts().stream().map(this::productView).toList());
        body.put("logs", store.logs());
        body.put("summary", summary());
        body.put("categories", store.products().stream().map(Product::category).distinct().toList());
        return body;
    }

    @GetMapping("/api/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "") String category) {
        List<Map<String, Object>> items = new ArrayList<>();
        var served = adService.pick(q, category);
        long sponsoredId = served.map(s -> s.product().id()).orElse(-1L);
        served.ifPresent(s -> items.add(hit(s.product(), true, s.campaign().rankScore(), s.campaign())));
        if (q.isBlank()) {
            store.products().stream()
                    .filter(p -> category.isBlank() || p.category().equals(category))
                    .forEach(p -> items.add(hit(p, false, 0, null)));
        } else {
            searchService.search(q, 20).forEach((id, score) -> {
                Product product = store.product(id);
                if (id != sponsoredId && (category.isBlank() || product.category().equals(category))) {
                    items.add(hit(product, false, score, null));
                }
            });
        }
        List<Map<String, Object>> auction = adService.explain(q).stream().map(row -> {
            Map<String, Object> view = adView(row.campaign());
            view.put("matched", row.matched());
            view.put("score", Math.round(row.score() * 100.0) / 100.0);
            return view;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", q);
        body.put("category", category);
        body.put("items", items);
        body.put("auction", auction);
        return body;
    }

    @GetMapping("/api/recommend")
    public Map<String, Object> recommend(@RequestParam String userId,
                                         @RequestParam(defaultValue = "cf") String mode) {
        var items = recommendService.recommend(userId, 4, mode).stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("product", productView(s.product()));
            row.put("score", Math.round(s.score() * 100.0) / 100.0);
            row.put("reason", s.reason());
            return row;
        }).toList();
        return Map.of("userId", userId, "mode", mode, "items", items);
    }

    @PostMapping("/api/ads/{id}/click")
    public Map<String, Object> clickAd(@PathVariable long id) {
        return adView(adService.charge(id));
    }

    @PostMapping("/api/ads/{id}/bid")
    public Map<String, Object> bid(@PathVariable long id, @RequestBody Map<String, Integer> body) {
        return adView(adService.updateBid(id, body.getOrDefault("bidCents", 1)));
    }

    @PostMapping("/api/ads/{id}/enabled")
    public Map<String, Object> enabled(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        return adView(adService.setEnabled(id, Boolean.TRUE.equals(body.get("enabled"))));
    }

    @PostMapping("/api/products")
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> body) {
        inventoryService.create(
                str(body, "name"),
                str(body, "category"),
                yuanToCents(body.get("priceYuan")),
                yuanToCents(body.get("costYuan")),
                num(body, "stock"),
                num(body, "warnBelow"),
                str(body, "description"));
        return bootstrap();
    }

    @PostMapping("/api/stock/in")
    public Map<String, Object> stockIn(@RequestBody Map<String, Object> body) {
        long skuId = ((Number) body.get("skuId")).longValue();
        int qty = ((Number) body.get("qty")).intValue();
        String note = body.get("note") == null ? "" : body.get("note").toString();
        inventoryService.purchaseIn(skuId, qty, note);
        return bootstrap();
    }

    @PostMapping("/api/stock/out")
    public Map<String, Object> stockOut(@RequestBody Map<String, Object> body) {
        long skuId = ((Number) body.get("skuId")).longValue();
        int qty = ((Number) body.get("qty")).intValue();
        String userId = body.get("userId") == null ? "" : body.get("userId").toString();
        inventoryService.saleOut(skuId, qty, userId);
        return bootstrap();
    }

    @PostMapping("/api/stock/adjust")
    public Map<String, Object> adjust(@RequestBody Map<String, Object> body) {
        inventoryService.adjust(((Number) body.get("skuId")).longValue(), ((Number) body.get("counted")).intValue());
        return bootstrap();
    }

    @PostMapping("/api/reset")
    public Map<String, Object> reset() {
        inventoryService.reset();
        return bootstrap();
    }

    @PostMapping("/api/media/tone")
    public Map<String, Object> tone(@RequestBody Map<String, Double> body) {
        double freq = body.getOrDefault("freq", 440.0);
        double seconds = body.getOrDefault("seconds", 1.0);
        double amplitude = body.getOrDefault("amplitude", 0.5);
        return wavBody(WavProcessor.sine(freq, seconds, amplitude));
    }

    @PostMapping("/api/media/gain")
    public Map<String, Object> gain(@RequestBody Map<String, Object> body) {
        byte[] wav = decodeWav(body);
        double gain = ((Number) body.getOrDefault("gain", 0.5)).doubleValue();
        return wavBody(WavProcessor.applyGain(wav, gain));
    }

    @PostMapping("/api/media/fade")
    public Map<String, Object> fade(@RequestBody Map<String, Object> body) {
        byte[] wav = decodeWav(body);
        double in = ((Number) body.getOrDefault("fadeIn", 0.2)).doubleValue();
        double out = ((Number) body.getOrDefault("fadeOut", 0.2)).doubleValue();
        return wavBody(WavProcessor.fade(wav, in, out));
    }

    @PostMapping("/api/media/trim")
    public Map<String, Object> trim(@RequestBody Map<String, Object> body) {
        byte[] wav = decodeWav(body);
        double start = ((Number) body.getOrDefault("start", 0)).doubleValue();
        double duration = ((Number) body.getOrDefault("duration", 0.4)).doubleValue();
        return wavBody(WavProcessor.trim(wav, start, duration));
    }

    @PostMapping("/api/media/mix")
    public Map<String, Object> mix(@RequestBody Map<String, Object> body) {
        byte[] wav = decodeWav(body);
        double freq = ((Number) body.getOrDefault("freq", 220)).doubleValue();
        double seconds = WavProcessor.analyze(wav).durationSec();
        byte[] bed = WavProcessor.sine(freq, seconds, 0.25);
        return wavBody(WavProcessor.mix(wav, bed, 0.6));
    }

    @GetMapping("/api/media/recipes")
    public Map<String, Object> recipes() {
        return Map.of(
                "transcode", FfmpegRecipes.transcode("ad.mp4", "ad-h264.mp4"),
                "thumbnail", FfmpegRecipes.thumbnail("ad.mp4", "cover.jpg"),
                "extractWav", FfmpegRecipes.extractWav("ad.mp4", "voice.wav"),
                "trim", FfmpegRecipes.trim("ad.mp4", "00:00:03", "15", "clip.mp4")
        );
    }

    private Map<String, Object> wavBody(byte[] wav) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("info", WavProcessor.analyze(wav));
        body.put("wavBase64", Base64.getEncoder().encodeToString(wav));
        return body;
    }

    private static byte[] decodeWav(Map<String, Object> body) {
        Object raw = body.get("wavBase64");
        if (raw == null) {
            throw new IllegalArgumentException("缺少 wavBase64");
        }
        return Base64.getDecoder().decode(raw.toString());
    }

    private Map<String, Object> hit(Product product, boolean sponsored, double score, AdCampaign ad) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("product", productView(product));
        row.put("sponsored", sponsored);
        row.put("score", Math.round(score * 100.0) / 100.0);
        if (ad != null) {
            row.put("adId", ad.id());
            row.put("bidYuan", yuan(ad.bidCents()));
            row.put("pctr", Math.round(ad.pctr() * 1000.0) / 10.0);
            row.put("rankScore", Math.round(ad.rankScore() * 100.0) / 100.0);
        }
        return row;
    }

    private Map<String, Object> productView(Product product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.id());
        row.put("name", product.name());
        row.put("category", product.category());
        row.put("priceYuan", yuan(product.priceCents()));
        row.put("costYuan", yuan(product.costCents()));
        row.put("marginYuan", yuan(product.marginCents()));
        row.put("stock", product.stock());
        row.put("warnBelow", product.warnBelow());
        row.put("lowStock", product.lowStock());
        row.put("description", product.description());
        return row;
    }

    private Map<String, Object> adView(AdCampaign ad) {
        Product product = store.product(ad.skuId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ad.id());
        row.put("skuId", ad.skuId());
        row.put("name", product.name());
        row.put("keywords", ad.keywords());
        row.put("bidCents", ad.bidCents());
        row.put("bidYuan", yuan(ad.bidCents()));
        row.put("budgetYuan", yuan(ad.budgetCents()));
        row.put("spentYuan", yuan(ad.spentCents()));
        row.put("remainingYuan", yuan(ad.remainingCents()));
        row.put("clicks", ad.clicks());
        row.put("impressions", ad.impressions());
        row.put("pctr", Math.round(ad.pctr() * 1000.0) / 10.0);
        row.put("rankScore", Math.round(ad.rankScore() * 100.0) / 100.0);
        row.put("enabled", ad.enabled());
        return row;
    }

    private Map<String, Object> summary() {
        int adSpent = store.ads().stream().mapToInt(AdCampaign::spentCents).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("revenueYuan", yuan(store.revenueCents()));
        row.put("profitYuan", yuan(store.profitCents()));
        row.put("adSpentYuan", yuan(adSpent));
        return row;
    }

    private static String yuan(int cents) {
        return String.format(Locale.CHINA, "%.2f", cents / 100.0);
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }

    private static int num(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        return (int) Math.round(Double.parseDouble(value.toString()));
    }

    private static int yuanToCents(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("请填写金额");
        }
        return (int) Math.round(Double.parseDouble(value.toString()) * 100);
    }
}
