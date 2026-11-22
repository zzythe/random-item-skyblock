package dev.farskies.qa;

import com.mojang.authlib.GameProfile;
import dev.farskies.CampaignData;
import dev.farskies.Settings;
import dev.farskies.WorldSetup;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("farskies")
@PrefixGameTestTemplate(false)
public final class CampaignGameTests {
    @GameTest(template = "empty") public static void stateSurvivesSaveLoad(GameTestHelper helper) {
        CampaignData data = new CampaignData(); data.clock = 17171; data.receipts = 3001; data.era = 2;
        UUID id = UUID.fromString("4631db6e-7269-4cc4-9dcc-76c698e462e6");
        var a = data.account(id); a.faction = 1; a.starterGranted=true; a.received = 87; a.supply.bank = 8; a.supply.anchor = 17000; a.activeTicks=12800; a.lastActiveMillis=7654321;
        data.trader=new int[]{2,152,96};data.communityChest=new int[]{-3,152,96};data.marketBounds=new int[]{-9,142,17,9,160,31};data.marketBlocks.add(5L);data.marketColumns.add(6L);data.islands.add("ordinary birch");
        CampaignData loaded = CampaignData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.receipts == 3001 && loaded.era == 2 && loaded.clock == 17171, "World progression did not round-trip");
        var b = loaded.players.get(id);
        helper.assertTrue(b.faction == 1 && b.starterGranted && b.received == 87 && b.supply.bank == 8 && b.supply.anchor == 17000 && b.seed == a.seed && b.activeTicks==12800 && b.lastActiveMillis==7654321, "Player state did not round-trip");
        helper.assertTrue(loaded.trader[2]==96 && loaded.communityChest[0]==-3 && loaded.marketBlocks.contains(5L) && loaded.marketColumns.contains(6L) && loaded.islands.equals(data.islands),"Map metadata did not round-trip");
        helper.succeed();
    }

    @GameTest(template = "empty") public static void externalStructuresExist(GameTestHelper helper) {
        var home = WorldSetup.required(helper.getLevel(), "skyvoid_biome_islands:birch_forest");
        var market = WorldSetup.required(helper.getLevel(), "skyvoid_biome_islands:sunflower_plains");
        helper.assertTrue(home.getSize().getX() > 3 && market.getSize().getX() > 3, "External mod templates are empty");
        helper.assertTrue(home.getSize().getY() + Settings.INSTANCE.spawnY < 320 && market.getSize().getY() + Settings.INSTANCE.spawnY < 320, "External templates exceed build height");
        helper.succeed();
    }
}
