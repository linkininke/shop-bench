package com.shopbench.model;

import java.util.List;

public final class AdCampaign {
    private final long id;
    private final long skuId;
    private final List<String> keywords;
    private int bidCents;
    private final int budgetCents;
    private int spentCents;
    private int clicks;
    private int impressions;
    private boolean enabled;

    public AdCampaign(long id, long skuId, List<String> keywords, int bidCents, int budgetCents,
                      int clicks, int impressions) {
        this.id = id;
        this.skuId = skuId;
        this.keywords = List.copyOf(keywords);
        this.bidCents = bidCents;
        this.budgetCents = budgetCents;
        this.spentCents = 0;
        this.clicks = clicks;
        this.impressions = impressions;
        this.enabled = true;
    }

    public long id() { return id; }
    public long skuId() { return skuId; }
    public List<String> keywords() { return keywords; }
    public int bidCents() { return bidCents; }
    public int budgetCents() { return budgetCents; }
    public int spentCents() { return spentCents; }
    public int clicks() { return clicks; }
    public int impressions() { return impressions; }
    public boolean enabled() { return enabled; }
    public int remainingCents() { return budgetCents - spentCents; }

    public void setBidCents(int bidCents) {
        if (bidCents <= 0) {
            throw new IllegalArgumentException("出价必须大于 0");
        }
        this.bidCents = bidCents;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && remainingCents() < bidCents) {
            throw new IllegalArgumentException("预算不够一次点击，不能恢复投放");
        }
        this.enabled = enabled;
    }

    public double pctr() {
        return (clicks + 1.0) / (impressions + 50.0);
    }

    public double rankScore() {
        return bidCents * pctr();
    }

    public boolean canServe() {
        return enabled && remainingCents() >= bidCents;
    }

    public void recordImpression() {
        impressions++;
    }

    public void chargeClick() {
        if (!canServe()) {
            throw new IllegalArgumentException("广告预算不足或已暂停");
        }
        spentCents += bidCents;
        clicks++;
        if (remainingCents() < bidCents) {
            enabled = false;
        }
    }
}
