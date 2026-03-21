package com.synthdetect.user.model;

import lombok.Getter;

@Getter
public enum UserPlan {

    FREE(500, 30),
    STARTER(10_000, 120),
    BUSINESS(100_000, 600),
    ENTERPRISE(-1, 3000); // -1 = unlimited

    private final int monthlyQuota;
    private final int rateLimitRpm;

    UserPlan(int monthlyQuota, int rateLimitRpm) {
        this.monthlyQuota = monthlyQuota;
        this.rateLimitRpm = rateLimitRpm;
    }

    public boolean isUnlimited() {
        return this.monthlyQuota == -1;
    }
}
