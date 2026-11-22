package dev.farskies.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SupplyClockTest {
    @Test void repeatedAccrualDoesNotMintAgain() {
        var clock = new SupplyClock(0, 0);
        clock.accrue(600, 600, 1); clock.accrue(600, 600, 1);
        assertEquals(1, clock.bank); assertEquals(600, clock.anchor);
        clock.accrue(1199, 600, 1); assertEquals(1, clock.bank);
    }
    @Test void oneOverdueRollCannotHideABurstBacklog() {
        var clock=new SupplyClock(0,0);
        clock.accrue(900000,600,1);assertEquals(1,clock.bank);
        assertTrue(clock.commitDelivery(true));
        clock.accrue(900000,600,1);assertEquals(0,clock.bank);
        clock.accrue(900599,600,1);assertEquals(0,clock.bank);
        clock.accrue(900600,600,1);assertEquals(1,clock.bank);
    }
    @Test void fullInventoryNeverSpendsAnItem() {
        var clock = new SupplyClock(0, 1);
        assertFalse(clock.commitDelivery(false)); assertEquals(1, clock.bank);
        assertTrue(clock.commitDelivery(true)); assertEquals(0, clock.bank);
        assertFalse(clock.commitDelivery(true));
    }
    @Test void serverDowntimeDoesNotAdvanceTheClock() {
        var clock = new SupplyClock(3000, 0);
        clock.accrue(3000, 600, 1); assertEquals(0, clock.bank);
        clock.accrue(3600, 600, 1); assertEquals(1, clock.bank);
    }
    @Test void clockRollbackCannotCreateNegativeBankOrOverflow() {
        var clock = new SupplyClock(9000, 1);
        clock.accrue(8000, 600, 1); assertEquals(1, clock.bank); assertEquals(8000, clock.anchor);
    }
}
