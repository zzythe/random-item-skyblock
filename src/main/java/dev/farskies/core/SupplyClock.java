package dev.farskies.core;

/** A tick clock: no wall-clock changes, catch-up storm, or duplicate accrual. */
public final class SupplyClock {
    public long anchor;
    public int bank;

    public SupplyClock(long anchor, int bank) { this.anchor = anchor; this.bank = bank; }

    public void accrue(long now, long interval, int cap) {
        if (interval < 1 || cap < 1) throw new IllegalArgumentException("Invalid supply clock");
        if (now < anchor) { anchor = now; return; }
        long count = (now - anchor) / interval;
        if (count == 0) return;
        bank = (int) Math.min(cap, bank + count);
        anchor = bank == cap ? now : anchor + count * interval;
    }

    public boolean commitDelivery(boolean fits) {
        if (!fits || bank == 0) return false;
        bank--;
        return true;
    }
}
