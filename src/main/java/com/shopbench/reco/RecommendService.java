package com.shopbench.reco;

import com.shopbench.model.Product;
import com.shopbench.search.Tokenizer;
import com.shopbench.store.ShopStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendService {
    private final ShopStore store;

    public RecommendService(ShopStore store) {
        this.store = store;
    }

    public synchronized List<Suggestion> recommend(String userId, int limit) {
        return recommend(userId, limit, "cf");
    }

    public synchronized List<Suggestion> recommend(String userId, int limit, String mode) {
        if ("content".equals(mode)) {
            return content(userId, limit);
        }
        return collaborative(userId, limit);
    }

    private List<Suggestion> collaborative(String userId, int limit) {
        Map<String, Set<Long>> purchases = store.purchases();
        Set<Long> mine = purchases.getOrDefault(userId, Set.of());
        if (mine.isEmpty()) {
            return popular(limit, "还没有购买记录，先看大家都在买的");
        }
        Map<Long, Set<String>> buyers = new HashMap<>();
        purchases.forEach((user, items) -> items.forEach(sku ->
                buyers.computeIfAbsent(sku, k -> new HashSet<>()).add(user)));

        Map<Long, Double> scores = new HashMap<>();
        Map<Long, String> reasons = new HashMap<>();
        for (long owned : mine) {
            Set<String> owners = buyers.getOrDefault(owned, Set.of());
            for (long candidate : buyers.keySet()) {
                if (mine.contains(candidate)) {
                    continue;
                }
                Set<String> other = buyers.get(candidate);
                long dot = owners.stream().filter(other::contains).count();
                if (dot == 0) {
                    continue;
                }
                double sim = dot / Math.sqrt(owners.size() * (double) other.size());
                scores.merge(candidate, sim, Double::sum);
                reasons.putIfAbsent(candidate, "买过" + store.product(owned).name() + "的人也买了它");
            }
        }
        if (scores.isEmpty()) {
            return popular(limit, "相似用户还不够，先看热销");
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new Suggestion(store.product(e.getKey()), e.getValue(), reasons.get(e.getKey())))
                .toList();
    }

    private List<Suggestion> content(String userId, int limit) {
        Set<Long> mine = store.purchases().getOrDefault(userId, Set.of());
        if (mine.isEmpty()) {
            return popular(limit, "还没有购买记录，先按大家都在买的推荐");
        }
        Set<String> categories = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        String anchor = store.product(mine.iterator().next()).name();
        for (long id : mine) {
            Product owned = store.product(id);
            categories.add(owned.category());
            tokens.addAll(Tokenizer.tokenize(owned.searchText()));
            anchor = owned.name();
        }
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, String> reasons = new HashMap<>();
        for (Product candidate : store.products()) {
            if (mine.contains(candidate.id())) {
                continue;
            }
            double score = categories.contains(candidate.category()) ? 2 : 0;
            long overlap = Tokenizer.tokenize(candidate.searchText()).stream().filter(tokens::contains).count();
            score += overlap * 0.1;
            if (score <= 0) {
                continue;
            }
            scores.put(candidate.id(), score);
            reasons.put(candidate.id(), categories.contains(candidate.category())
                    ? "和买过的「" + anchor + "」是同一类目"
                    : "标题和买过的商品有相同的词");
        }
        if (scores.isEmpty()) {
            return popular(limit, "没有内容上相近的商品");
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new Suggestion(store.product(e.getKey()), e.getValue(), reasons.get(e.getKey())))
                .toList();
    }

    private List<Suggestion> popular(int limit, String reason) {
        Map<Long, Integer> freq = new HashMap<>();
        store.purchases().values().forEach(items -> items.forEach(id -> freq.merge(id, 1, Integer::sum)));
        List<Product> ranked = new ArrayList<>(store.products());
        ranked.sort(Comparator.comparingInt((Product p) -> freq.getOrDefault(p.id(), 0)).reversed());
        return ranked.stream().limit(limit).map(p -> new Suggestion(p, freq.getOrDefault(p.id(), 0), reason)).toList();
    }

    public record Suggestion(Product product, double score, String reason) {}
}
