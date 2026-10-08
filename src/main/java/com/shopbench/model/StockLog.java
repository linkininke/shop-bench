package com.shopbench.model;

public record StockLog(long id, long skuId, String skuName, int delta, String type, String note, long atMillis) {
}
