package dev.farskies;

import net.minecraft.network.chat.Component;

/** One server-wide cycle per active interval, independent of roster size or item quantities. */
public final class Progression {
    public static long drops(CampaignData data){return data.progressTicks/(Settings.INSTANCE.intervalSeconds*20L);}
    public static int seconds(CampaignData data){long interval=Settings.INSTANCE.intervalSeconds*20L;return (int)((interval-data.progressTicks%interval+19)/20);}
    public static int tier(CampaignData data) {
        int tier=0;
        while(tier<5 && drops(data)>=Settings.INSTANCE.tierThresholds[tier+1])tier++;
        return tier;
    }
    public static long next(CampaignData data) {
        int tier=tier(data);
        return tier==5?drops(data):Settings.INSTANCE.tierThresholds[tier+1];
    }
    public static Component status(CampaignData data) {
        int tier=tier(data);
        return Component.literal("Tier "+(tier+1)+" | "+drops(data)+" shared drops"+(tier<5?" | "+(next(data)-drops(data))+" drops to Tier "+(tier+2):" | All tiers unlocked"));
    }
}
