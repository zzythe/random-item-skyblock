package dev.farskies;

import chanceCubes.config.CCubesSettings;
import chanceCubes.registry.global.GlobalCCRewardRegistry;
import chanceCubes.rewards.IChanceCubeReward;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.fml.loading.FMLPaths;

/** Chance Cubes supplies the blocks, components, placement, scanner, and activation engine. */
public final class LuckyEvents {
    public record Event(String id, String title, String grade, String kind, int minTier, List<Settings.Drop> items, String entity, String effect, int count, String enchantment, int enchantLevel) {}
    private static List<Event> EVENTS = List.of();

    static void install() {
        try {
            var file = FMLPaths.CONFIGDIR.get().resolve("farskies-luck.json");
            if (!Files.exists(file)) try (var in = LuckyEvents.class.getResourceAsStream("/farskies-luck.json")) { if (in == null) throw new IllegalStateException("Missing lucky events"); Files.copy(in, file); }
            EVENTS = List.of(new Gson().fromJson(Files.readString(file), Event[].class));
            if (EVENTS.isEmpty()) throw new IllegalStateException("Empty lucky pool");
            for (Event event : EVENTS) {
                if (!List.of("good", "mid", "bad").contains(event.grade) || !List.of("items", "mob", "effect", "tnt", "enchant").contains(event.kind) || event.minTier < 0 || event.minTier > 5 || event.count > 6 || event.enchantLevel > 5) throw new IllegalStateException("Invalid lucky event " + event.id);
                for (Settings.Drop item : event.items) Campaign.item(item.item());
                if (event.kind.equals("mob") && !BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(event.entity))) throw new IllegalStateException("Unknown entity in " + event.id);
                if (event.kind.equals("effect") && !BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(event.effect))) throw new IllegalStateException("Unknown effect in " + event.id);
                if (event.kind.equals("enchant")) Campaign.item(event.entity);
            }
        } catch (Exception e) { throw new IllegalStateException("Invalid config/farskies-luck.json", e); }
        // Old profiles disabled natives. Restore upstream rewards in memory without
        // replacing their saved settings, then add our outcomes alongside them.
        CCubesSettings.enableHardCodedRewards.set(true);
        CCubesSettings.disableGiantCC.set(false);
        chanceCubes.rewards.DefaultRewards.loadDefaultRewards(serverRegistry());
        chanceCubes.rewards.DefaultGiantRewards.loadDefaultRewards(serverRegistry());
        for (int luck = -100; luck <= 100; luck += 25) {
            final int value = luck;
            IChanceCubeReward dispatch = new IChanceCubeReward() {
                public String getName() { return "farskies:luck_" + (value + 100); }
                public int getChanceValue() { return value; }
                public void trigger(ServerLevel level, BlockPos pos, Player player, JsonObject ignored) { roll(level, pos, player, value); }
            };
            GlobalCCRewardRegistry.DEFAULT.unregisterReward(dispatch.getName());
            GlobalCCRewardRegistry.GIANT.unregisterReward(dispatch.getName());
            GlobalCCRewardRegistry.DEFAULT.registerReward(dispatch);
            GlobalCCRewardRegistry.GIANT.registerReward(dispatch);
        }
        FarSkies.LOG.info("CHANCE_POOL default={} giant={} customOutcomes={}",GlobalCCRewardRegistry.DEFAULT.getRewardNames().size(),GlobalCCRewardRegistry.GIANT.getRewardNames().size(),EVENTS.size());
    }

    private static net.minecraft.core.HolderLookup.Provider serverRegistry(){return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess();}
    static void uninstall(){
        for(int luck=-100;luck<=100;luck+=25){String name="farskies:luck_"+(luck+100);GlobalCCRewardRegistry.DEFAULT.unregisterReward(name);GlobalCCRewardRegistry.GIANT.unregisterReward(name);}
    }

    public static void roll(ServerLevel level, BlockPos pos, Player player, int luck) {
        if (!Campaign.enabled() || !(player instanceof ServerPlayer p)) return;
        if (Market.inside(level, pos, 12) || Market.inside(p.level(), p.blockPosition())) {
            p.getInventory().add(Shop.cube(luck)); p.sendSystemMessage(Component.literal("Move beyond the Market buffer to open lucky blocks.")); return;
        }
        int tier = Progression.tier(CampaignData.get(p.server));
        int n = level.random.nextInt(100);
        String grade = luck <= -50 ? (n < 15 ? "good" : n < 25 ? "mid" : "bad") : luck >= 50 ? (n < 80 ? "good" : n < 95 ? "mid" : "bad") : (n < 60 ? "good" : n < 85 ? "mid" : "bad");
        var pool = EVENTS.stream().filter(e -> e.grade.equals(grade) && e.minTier <= tier).toList();
        if (pool.isEmpty()) throw new IllegalStateException("No eligible " + grade + " lucky outcomes");
        Event event = pool.get(level.random.nextInt(pool.size()));
        p.sendSystemMessage(Component.literal(event.title));
        int multiplier = grade.equals("good") ? (luck <= -50 ? 3 : luck >= 50 ? 2 : 1) : 1;
        for (Settings.Drop item : event.items) give(level, pos, p, new ItemStack(Campaign.item(item.item()), item.count() * multiplier));
        switch (event.kind) {
            case "mob" -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(event.entity));
                for (int i = 0; i < Math.max(1, event.count); i++) {
                    var mob = type.create(level); if (mob == null) continue;
                    mob.moveTo(pos.getX() + .5 + i % 3, pos.getY() + 1, pos.getZ() + .5 + i / 3, 0, 0);
                    mob.setCustomName(Component.literal(event.title));
                    if (event.id.equals("debt_collector")) mob.getPersistentData().putBoolean("farskies_bounty", true);
                    level.addFreshEntity(mob);
                }
            }
            case "effect" -> {
                var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(event.effect)).orElseThrow();
                p.addEffect(new MobEffectInstance(effect, Math.max(1, event.count) * 100, 0));
                if (event.effect.equals("minecraft:levitation")) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0));
            }
            case "tnt" -> { PrimedTnt tnt = new PrimedTnt(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, p); tnt.setFuse(100); level.addFreshEntity(tnt); }
            case "enchant" -> {
                ItemStack tool = new ItemStack(Campaign.item(event.entity));
                var key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(event.enchantment));
                tool.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), event.enchantLevel);
                tool.set(DataComponents.CUSTOM_NAME, Component.literal(event.title)); give(level, pos, p, tool);
            }
            default -> {}
        }
        FarSkies.LOG.info("LUCK {} event={} luck={} era={} at={}", p.getUUID(), event.id, luck, tier, pos);
    }

    static void give(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        ItemStack original = stack.copy();
        player.getInventory().add(stack);
        int inserted = original.getCount() - stack.getCount();
        if (inserted > 0) PickupNotice.send(player, original.copyWithCount(inserted));
        if (!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, stack));
    }

    @SubscribeEvent public static void bounty(LivingDropsEvent event) {
        if (!event.getEntity().getPersistentData().getBoolean("farskies_bounty")) return;
        var mob = event.getEntity(); event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 8)));
    }
}
