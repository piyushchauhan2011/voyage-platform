package com.voyage.app.hotel;

/**
 * Hotel SaaS subscription plan — an ABAC attribute on {@link Hotel}.
 * Gates how many hotels a manager may own and which write/refund features unlock.
 */
public enum SaasPlan {
    FREE(1, false, false),
    PRO(5, true, false),
    ENTERPRISE(Integer.MAX_VALUE, true, true);

    private final int maxHotels;
    private final boolean inventoryWritesAllowed;
    private final boolean refundsAllowed;

    SaasPlan(int maxHotels, boolean inventoryWritesAllowed, boolean refundsAllowed) {
        this.maxHotels = maxHotels;
        this.inventoryWritesAllowed = inventoryWritesAllowed;
        this.refundsAllowed = refundsAllowed;
    }

    public int getMaxHotels() {
        return maxHotels;
    }

    public boolean isInventoryWritesAllowed() {
        return inventoryWritesAllowed;
    }

    public boolean isRefundsAllowed() {
        return refundsAllowed;
    }

    public boolean isAtLeast(SaasPlan other) {
        return this.ordinal() >= other.ordinal();
    }
}
