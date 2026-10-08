package com.shopbench.ads;

import com.shopbench.model.AdCampaign;
import com.shopbench.model.Product;
import com.shopbench.search.Tokenizer;
import com.shopbench.store.ShopStore;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AdService {
    private final ShopStore store;

    public AdService(ShopStore store) {
        this.store = store;
    }

    public synchronized Optional<ServedAd> pick(String query) {
        return pick(query, "");
    }

    public synchronized Optional<ServedAd> pick(String query, String category) {
        return ranked(query).stream()
                .filter(row -> row.matched() && row.campaign().canServe())
                .filter(row -> category == null || category.isBlank() || store.product(row.campaign().skuId()).category().equals(category))
                .findFirst()
                .map(row -> {
                    row.campaign().recordImpression();
                    return new ServedAd(row.campaign(), store.product(row.campaign().skuId()));
                });
    }

    public synchronized List<AuctionRow> explain(String query) {
        return ranked(query);
    }

    private List<AuctionRow> ranked(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> terms = Set.copyOf(Tokenizer.tokenize(query));
        return store.ads().stream()
                .map(ad -> new AuctionRow(ad, matches(ad, terms), ad.rankScore()))
                .sorted(Comparator.comparing((AuctionRow row) -> row.matched() && row.campaign().canServe()).reversed()
                        .thenComparing(Comparator.comparingDouble(AuctionRow::score).reversed()))
                .toList();
    }

    public synchronized AdCampaign charge(long adId) {
        AdCampaign ad = store.ad(adId);
        ad.chargeClick();
        return ad;
    }

    public synchronized AdCampaign updateBid(long adId, int bidCents) {
        AdCampaign ad = store.ad(adId);
        ad.setBidCents(bidCents);
        return ad;
    }

    public synchronized AdCampaign setEnabled(long adId, boolean enabled) {
        AdCampaign ad = store.ad(adId);
        ad.setEnabled(enabled);
        return ad;
    }

    private boolean matches(AdCampaign ad, Set<String> terms) {
        for (String keyword : ad.keywords()) {
            for (String token : Tokenizer.tokenize(keyword)) {
                if (terms.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record ServedAd(AdCampaign campaign, Product product) {}

    public record AuctionRow(AdCampaign campaign, boolean matched, double score) {}
}
