package dev.farskies;

import dev.farskies.core.SupplyClock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class CampaignData extends SavedData {
    public long clock;
    public long receipts;
    public long progressTicks;
    public long nextEventAt,eventOpensAt,eventExpiresAt;
    public int eventIndex;
    public int contentVersion;
    public final java.util.Set<String> checkedIslands=new java.util.HashSet<>();
    public int[] eventPosition=new int[0],cacheWins=new int[2];
    public final java.util.List<Long> eventHistory=new java.util.ArrayList<>();
    public boolean initialized;
    public int layoutVersion;
    public int era;
    public long startedAtMillis;
    public final java.util.List<String> islands = new java.util.ArrayList<>();
    public int[] trader = new int[3];
    public int[] marketBounds = new int[6];
    public int[] communityChest = new int[3];
    public final java.util.Set<Long> marketBlocks = new java.util.HashSet<>();
    public final java.util.Set<Long> marketColumns = new java.util.HashSet<>();
    public CompoundTag marketOffers = new CompoundTag();
    public long marketRestock;
    public final Map<UUID, Account> players = new HashMap<>();
    public final int[][] spawns = new int[3][3];

    public static final class Account {
        public int faction = -1;
        public long received;
        public boolean starterGranted,clearBedOnLogin;
        public long activeTicks;
        public long lastActiveMillis;
        public SupplyClock supply;
        public long seed;
        public long fatigueUntil,graceUntil;
        public int pendingTier=-1;
        public Account(long now, long seed) { supply = new SupplyClock(now, 0); this.seed = seed; }
    }

    public static CampaignData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CampaignData::new, CampaignData::load), "farskies");
    }

    public Account account(UUID id) { return players.computeIfAbsent(id, key -> new Account(clock, java.util.concurrent.ThreadLocalRandom.current().nextLong())); }

    public static CampaignData load(CompoundTag tag, HolderLookup.Provider lookup) {
        CampaignData data = new CampaignData();
        data.clock = tag.getLong("clock"); data.receipts = tag.getLong("receipts");
        data.initialized = tag.getBoolean("initialized"); data.era = tag.getInt("era");
        data.layoutVersion=tag.getInt("layoutVersion");
        data.startedAtMillis = tag.getLong("startedAt");
        for (Tag island : tag.getList("islands", Tag.TAG_STRING)) data.islands.add(island.getAsString());
        if (tag.getIntArray("trader").length == 3) data.trader = tag.getIntArray("trader");
        if(tag.getIntArray("marketBounds").length==6)data.marketBounds=tag.getIntArray("marketBounds");
        if(tag.getIntArray("communityChest").length==3)data.communityChest=tag.getIntArray("communityChest");
        for(long p:tag.getLongArray("marketBlocks"))data.marketBlocks.add(p);
        for(long p:tag.getLongArray("marketColumns"))data.marketColumns.add(p);
        data.marketOffers=tag.getCompound("marketOffers");data.marketRestock=tag.getLong("marketRestock");
        for (int i = 0; i < 3; i++) { int[] pos = tag.getIntArray("spawn" + i); if (pos.length == 3) data.spawns[i] = pos; }
        for (Tag entry : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) entry;
            Account a = new Account(t.getLong("anchor"), t.getLong("seed"));
            a.starterGranted=t.getBoolean("starterGranted");a.clearBedOnLogin=t.getBoolean("clearBedOnLogin"); a.supply.bank = t.getInt("bank"); a.faction = t.getInt("faction"); a.received = t.getLong("received");
            a.activeTicks=t.getLong("activeTicks"); a.lastActiveMillis=t.getLong("lastActive");
            a.fatigueUntil=t.getLong("fatigueUntil");a.pendingTier=t.contains("pendingTier")?t.getInt("pendingTier"):-1;
            data.players.put(t.getUUID("uuid"), a);
        }
        data.progressTicks=tag.contains("progressTicks")?tag.getLong("progressTicks"):data.players.values().stream().mapToLong(a->a.received).max().orElse(0)*Settings.INSTANCE.intervalSeconds*20L;
        data.nextEventAt=tag.getLong("nextEventAt");data.eventOpensAt=tag.getLong("eventOpensAt");data.eventExpiresAt=tag.getLong("eventExpiresAt");data.eventIndex=tag.getInt("eventIndex");data.eventPosition=tag.getIntArray("eventPosition");
        data.contentVersion=tag.getInt("contentVersion");for(Tag entry:tag.getList("checkedIslands",Tag.TAG_STRING))data.checkedIslands.add(entry.getAsString());
        if(tag.getIntArray("cacheWins").length==2)data.cacheWins=tag.getIntArray("cacheWins");for(long pos:tag.getLongArray("eventHistory"))data.eventHistory.add(pos);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLong("clock", clock); tag.putLong("receipts", receipts); tag.putBoolean("initialized", initialized); tag.putInt("era", era);
        tag.putLong("progressTicks",progressTicks);
        tag.putInt("contentVersion",contentVersion);var checked=new ListTag();checkedIslands.forEach(s->checked.add(net.minecraft.nbt.StringTag.valueOf(s)));tag.put("checkedIslands",checked);
        tag.putLong("nextEventAt",nextEventAt);tag.putLong("eventOpensAt",eventOpensAt);tag.putLong("eventExpiresAt",eventExpiresAt);tag.putInt("eventIndex",eventIndex);tag.putIntArray("eventPosition",eventPosition);tag.putIntArray("cacheWins",cacheWins);tag.putLongArray("eventHistory",eventHistory.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("layoutVersion",layoutVersion);
        tag.putLong("startedAt",startedAtMillis);tag.putIntArray("trader",trader);
        tag.putIntArray("marketBounds",marketBounds);tag.putIntArray("communityChest",communityChest);
        tag.putLongArray("marketBlocks",marketBlocks.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("marketColumns",marketColumns.stream().mapToLong(Long::longValue).toArray());
        tag.put("marketOffers",marketOffers);tag.putLong("marketRestock",marketRestock);
        ListTag islandList=new ListTag();islands.forEach(s->islandList.add(net.minecraft.nbt.StringTag.valueOf(s)));tag.put("islands",islandList);
        for (int i = 0; i < 3; i++) tag.putIntArray("spawn" + i, spawns[i]);
        ListTag list = new ListTag();
        players.forEach((id, a) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("uuid", id); t.putInt("faction", a.faction);
            t.putBoolean("starterGranted",a.starterGranted);t.putBoolean("clearBedOnLogin",a.clearBedOnLogin);t.putLong("activeTicks",a.activeTicks);t.putLong("lastActive",a.lastActiveMillis);
            t.putLong("anchor", a.supply.anchor); t.putInt("bank", a.supply.bank); t.putLong("seed", a.seed); t.putLong("received", a.received);
            t.putLong("fatigueUntil",a.fatigueUntil);t.putInt("pendingTier",a.pendingTier);
            list.add(t);
        });
        tag.put("players", list);
        return tag;
    }
}
