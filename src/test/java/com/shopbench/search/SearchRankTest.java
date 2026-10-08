package com.shopbench.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRankTest {
    @Test
    void honeyQueryRanksHoneyBeforeUnrelated() {
        InvertedIndex index = new InvertedIndex();
        index.add(102, "太行山蜂蜜 滋补 槐花蜜");
        index.add(110, "蜂蜜柚子茶 饮品 蜂蜜和柚子皮");
        index.add(104, "高平丝绸围巾 服饰 真丝");
        List<Long> top = index.top("蜂蜜", 3);
        assertTrue(top.contains(102L));
        assertTrue(top.contains(110L));
        assertTrue(!top.contains(104L));
    }

    @Test
    void scarfQueryHitsSilkItems() {
        InvertedIndex index = new InvertedIndex();
        index.add(104, "高平丝绸围巾 服饰");
        index.add(112, "丝绸礼盒 服饰 围巾加礼盒");
        index.add(106, "阳城陶瓷杯 日用");
        List<Long> top = index.top("丝绸围巾", 5);
        assertTrue(top.contains(104L));
        assertTrue(!top.contains(106L));
    }

    @Test
    void tokenizerKeepsChineseBigram() {
        List<String> tokens = Tokenizer.tokenize("蜂蜜");
        assertTrue(tokens.contains("蜂蜜"));
        assertTrue(tokens.contains("蜂"));
    }
}
