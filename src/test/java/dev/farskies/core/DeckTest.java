package dev.farskies.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DeckTest {
    @Test void savedSeedsRemainStableWhileNewSeedsChangeTheSequence() {
        int changed=0;
        for(int i=0;i<10000;i++) {
            assertEquals(Deck.index(78431,i,2000),Deck.index(78431,i,2000));
            if(Deck.index(78431,i,2000)!=Deck.index(98412,i,2000))changed++;
        }
        assertTrue(changed>9900);
    }
    @Test void independentDrawsCanRepeatAndStayWithinThePool() {
        boolean repeated=false;int previous=-1;
        for(int i=0;i<10000;i++) {
            int draw=Deck.index(5637,i,23);assertTrue(draw>=0 && draw<23);
            repeated|=draw==previous;previous=draw;
        }
        assertTrue(repeated,"Random draws accidentally became a forced no-repeat path");
    }
    @Test void tierUnlockUsesActualBoundaryAndNeverRunsPastFinalTier() {
        long[] thresholds = {0,800,3000,9000,22000,45000};
        assertEquals(0, Deck.tier(799, thresholds)); assertEquals(1, Deck.tier(800, thresholds));
        assertEquals(5, Deck.tier(Long.MAX_VALUE, thresholds));
    }
}
