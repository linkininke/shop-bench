package com.shopbench.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BM25 倒排检索。k1 控制词频饱和，b 控制对长文档的惩罚。
 * 商品量小，改商品后全量重建即可。
 */
public final class InvertedIndex {
    public static final double K1 = 1.2;
    public static final double B = 0.75;

    private final Map<String, List<Posting>> postings = new HashMap<>();
    private final Map<Long, Integer> docLen = new HashMap<>();
    private double avgLen;

    public void clear() {
        postings.clear();
        docLen.clear();
        avgLen = 0;
    }

    public void add(long docId, String text) {
        List<String> tokens = Tokenizer.tokenize(text);
        docLen.put(docId, tokens.size());
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        tf.forEach((term, count) -> postings.computeIfAbsent(term, k -> new ArrayList<>()).add(new Posting(docId, count)));
        int total = docLen.values().stream().mapToInt(Integer::intValue).sum();
        avgLen = docLen.isEmpty() ? 0 : (double) total / docLen.size();
    }

    public Map<Long, Double> search(String query) {
        List<String> terms = Tokenizer.tokenize(query).stream().distinct().toList();
        Map<Long, Double> scores = new HashMap<>();
        if (terms.isEmpty() || avgLen == 0) {
            return scores;
        }
        int n = docLen.size();
        for (String term : terms) {
            List<Posting> list = postings.get(term);
            if (list == null || list.isEmpty()) {
                continue;
            }
            double idf = Math.log((n - list.size() + 0.5) / (list.size() + 0.5) + 1);
            for (Posting posting : list) {
                int len = docLen.getOrDefault(posting.docId, 1);
                double tf = posting.tf;
                double norm = tf * (K1 + 1) / (tf + K1 * (1 - B + B * len / avgLen));
                scores.merge(posting.docId, idf * norm, Double::sum);
            }
        }
        return scores;
    }

    public List<Long> top(String query, int limit) {
        return search(query).entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Map<Long, Double> ranked(String query, int limit) {
        Map<Long, Double> ordered = new LinkedHashMap<>();
        search(query).entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    private record Posting(long docId, int tf) {}
}
