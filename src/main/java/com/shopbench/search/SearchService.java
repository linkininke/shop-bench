package com.shopbench.search;

import com.shopbench.model.Product;
import com.shopbench.store.ShopStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {
    private final ShopStore store;
    private final InvertedIndex index = new InvertedIndex();

    public SearchService(ShopStore store) {
        this.store = store;
        rebuild();
    }

    public synchronized void rebuild() {
        index.clear();
        for (Product product : store.products()) {
            index.add(product.id(), product.searchText());
        }
    }

    public synchronized Map<Long, Double> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            Map<Long, Double> hot = new LinkedHashMap<>();
            List<Product> products = new ArrayList<>(store.products());
            products.sort((a, b) -> Integer.compare(b.stock(), a.stock()));
            products.stream().limit(limit).forEach(p -> hot.put(p.id(), 0.0));
            return hot;
        }
        return index.ranked(query, limit);
    }
}
