package com.shopbench.model;

public final class Product {
    private final long id;
    private final String name;
    private final String category;
    private final int priceCents;
    private final int costCents;
    private int stock;
    private final int warnBelow;
    private final String description;

    public Product(long id, String name, String category, int priceCents, int costCents, int stock, int warnBelow, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.priceCents = priceCents;
        this.costCents = costCents;
        this.stock = stock;
        this.warnBelow = warnBelow;
        this.description = description;
    }

    public long id() { return id; }
    public String name() { return name; }
    public String category() { return category; }
    public int priceCents() { return priceCents; }
    public int costCents() { return costCents; }
    public int marginCents() { return priceCents - costCents; }
    public int stock() { return stock; }
    public int warnBelow() { return warnBelow; }
    public String description() { return description; }
    public boolean lowStock() { return stock <= warnBelow; }

    public void addStock(int qty) { this.stock += qty; }

    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("库存不能为负");
        }
        this.stock = stock;
    }

    public void removeStock(int qty) {
        if (qty > stock) {
            throw new IllegalArgumentException("库存不足：" + name + " 现有 " + stock + "，要出 " + qty);
        }
        this.stock -= qty;
    }

    public String searchText() {
        return name + " " + category + " " + description;
    }
}
