package dev.farskies.qa;

import com.mojang.authlib.GameProfile;
import dev.farskies.CampaignData;
import dev.farskies.FarSkies;
import dev.farskies.WorldSetup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Gallery director for the Modrinth screenshots. Never distributed (the jar excludes dev/farskies/qa).
 * Creates a disposable world, then runs a shot script line by line. More lines can be queued live by
 * writing gallery/queue.txt in the game directory; results go to gallery/out.txt.
 * Script commands: cmd, cmdv, look, player, fov, rd, gui, only, wait, shot, chat, deliver, actor, unactor,
 * pose, find, where, echo, quit.
 */
public final class GallerySmoke {
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static final Map<String, RemotePlayer> ACTORS = new HashMap<>();
    private static final Map<String, float[]> SWING = new HashMap<>();
    private static Set<String> onlyLayers = null;
    private static boolean hideHand;
    private static final Set<String> seenLayers = new HashSet<>();
    private static int stage, ticks, waitTicks, nextActorId = -50000;
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static Path dir;

    public static void register() {
        Minecraft.getInstance().execute(() -> org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GallerySmoke::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GallerySmoke::layer);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderHandEvent e) -> { if (hideHand) e.setCanceled(true); });
    }

    private static void out(String text) {
        FarSkies.LOG.info("GALLERY_QA {}", text);
        try { Files.writeString(dir.resolve("out.txt"), text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ignored) {}
    }
    private static MinecraftServer server(Minecraft mc) { return mc.getSingleplayerServer(); }
    private static ServerPlayer player(Minecraft mc) { return server(mc).getPlayerList().getPlayers().getFirst(); }

    static void layer(RenderGuiLayerEvent.Pre event) {
        var name = event.getName().toString();
        if (seenLayers.add(name)) out("layer " + name);
        if (onlyLayers != null && !onlyLayers.contains(name)) event.setCanceled(true);
    }

    private static void run(Minecraft mc, String command, boolean verbose) {
        var s = server(mc);
        CommandSource sink = new CommandSource() {
            public void sendSystemMessage(Component c) { out("  > " + c.getString()); }
            public boolean acceptsSuccess() { return verbose; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        s.execute(() -> {
            var p = player(mc);
            s.getCommands().performPrefixedCommand(p.createCommandSourceStack().withSource(sink).withPermission(4), command);
        });
    }

    private static double[] nums(String[] a, int from, int count) {
        double[] r = new double[count];
        for (int i = 0; i < count; i++) r[i] = Double.parseDouble(a[from + i]);
        return r;
    }

    private static float[] aim(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        return new float[] {(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    private static UUID skinUuid(String skin) {
        var random = new java.util.Random(skin.hashCode());
        for (int i = 0; i < 100000; i++) {
            var id = new UUID(random.nextLong(), random.nextLong());
            if (DefaultPlayerSkin.get(id).texture().getPath().contains(skin)) return id;
        }
        throw new IllegalArgumentException("No default skin " + skin);
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static ItemStack dyed(String id, int color) {
        var stack = item(id);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }

    /** actor NAME SKIN x y z yaw pitch [team=red|blue] [armor=chest|full|none] [main=item] [off=item] [crouch] [swing=0.4] */
    private static void actor(Minecraft mc, String[] a) {
        var name = a[1];
        var old = ACTORS.remove(name);
        if (old != null) mc.level.removeEntity(old.getId(), Entity.RemovalReason.DISCARDED);
        var p = new RemotePlayer(mc.level, new GameProfile(skinUuid(a[2]), name));
        double[] v = nums(a, 3, 5);
        p.setId(nextActorId--);
        p.moveTo(v[0], v[1], v[2], (float) v[3], (float) v[4]);
        p.setOldPosAndRot();
        p.setYHeadRot((float) v[3]); p.yHeadRotO = (float) v[3]; p.yBodyRot = (float) v[3]; p.yBodyRotO = (float) v[3];
        int color = 0;
        String armor = "chest";
        for (int i = 8; i < a.length; i++) {
            var kv = a[i].split("=", 2);
            switch (kv[0]) {
                case "team" -> color = kv[1].equals("red") ? 0xB02E26 : 0x3C44AA;
                case "armor" -> armor = kv[1];
                case "main" -> p.setItemSlot(EquipmentSlot.MAINHAND, item(kv[1]));
                case "off" -> p.setItemSlot(EquipmentSlot.OFFHAND, item(kv[1]));
                case "head" -> p.setItemSlot(EquipmentSlot.HEAD, item(kv[1]));
                case "crouch" -> { p.setPose(Pose.CROUCHING); p.setShiftKeyDown(true); }
                case "swing" -> SWING.put(name, new float[] {Float.parseFloat(kv[1])});
                case "body" -> { p.yBodyRot = Float.parseFloat(kv[1]); p.yBodyRotO = p.yBodyRot; }
                default -> out("unknown actor option " + a[i]);
            }
        }
        if (color != 0 && !armor.equals("none")) {
            p.setItemSlot(EquipmentSlot.CHEST, dyed("minecraft:leather_chestplate", color));
            if (armor.equals("full")) {
                p.setItemSlot(EquipmentSlot.LEGS, dyed("minecraft:leather_leggings", color));
                p.setItemSlot(EquipmentSlot.FEET, dyed("minecraft:leather_boots", color));
            }
        }
        mc.level.addEntity(p);
        ACTORS.put(name, p);
        out("actor " + name + " skin " + DefaultPlayerSkin.get(p.getUUID()).texture());
    }

    private static void find(Minecraft mc, String[] a) {
        var s = server(mc);
        s.execute(() -> {
            var l = s.overworld();
            var registry = l.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var matches = registry.holders().filter(h -> h.key().location().toString().contains(a[1])).map(h -> (net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>) h).toList();
            if (matches.isEmpty()) { out("find " + a[1] + ": no structure ids"); return; }
            var found = l.getChunkSource().getGenerator().findNearestMapStructure(l, HolderSet.direct(matches), new BlockPos(Integer.parseInt(a[2]), 150, Integer.parseInt(a[3])), a.length > 4 ? Integer.parseInt(a[4]) : 60, false);
            if (found == null) { out("find " + a[1] + ": none"); return; }
            var origin = found.getFirst();
            for (int x = (origin.getX() >> 4) - 4; x <= (origin.getX() >> 4) + 4; x++) for (int z = (origin.getZ() >> 4) - 4; z <= (origin.getZ() >> 4) + 4; z++) l.getChunk(x, z);
            var start = l.getChunk(origin).getStartForStructure(found.getSecond().value());
            out("find " + a[1] + ": " + found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?") + " at " + origin.toShortString() + (start != null && start.isValid() ? " box " + start.getBoundingBox() : ""));
        });
    }

    private static void exec(Minecraft mc, String line) throws Exception {
        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) return;
        String[] a = line.split("\\s+");
        switch (a[0]) {
            case "cmd" -> run(mc, line.substring(4), false);
            case "cmdv" -> run(mc, line.substring(5), true);
            case "look" -> { // look ex ey ez tx ty tz : spectator eye at e, looking at t
                double[] v = nums(a, 1, 6);
                var eye = new Vec3(v[0], v[1], v[2]); float[] r = aim(eye, new Vec3(v[3], v[4], v[5]));
                server(mc).execute(() -> { var p = player(mc); p.setGameMode(GameType.SPECTATOR); p.teleportTo(p.serverLevel(), eye.x, eye.y - p.getEyeHeight(), eye.z, r[0], r[1]); });
            }
            case "player" -> { // player x y z yaw pitch mode
                double[] v = nums(a, 1, 5); var mode = GameType.byName(a[6]);
                server(mc).execute(() -> { var p = player(mc); p.setGameMode(mode); p.teleportTo(p.serverLevel(), v[0], v[1], v[2], (float) v[3], (float) v[4]); });
            }
            case "fov" -> mc.options.fov().set(Integer.parseInt(a[1]));
            case "rd" -> { mc.options.renderDistance().set(Integer.parseInt(a[1])); mc.options.simulationDistance().set(Math.min(12, Integer.parseInt(a[1]))); }
            case "gui" -> mc.options.hideGui = !a[1].equals("on");
            case "only" -> onlyLayers = a[1].equals("all") ? null : new HashSet<>(List.of(a[1].split(",")));
            case "wait" -> waitTicks = Integer.parseInt(a[1]);
            case "shot" -> {
                int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
                mc.getToasts().clear();
                Screenshot.grab(mc.gameDirectory, a[1] + ".png", mc.getMainRenderTarget(), c -> {});
                out("SHOT " + a[1] + " " + w + "x" + h);
            }
            case "chat" -> {
                switch (a[1]) {
                    case "clear" -> mc.gui.getChat().clearMessages(false);
                    case "open" -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
                    default -> mc.setScreen(null);
                }
            }
            case "deliver" -> {
                int n = Integer.parseInt(a[1]);
                server(mc).execute(() -> {
                    try {
                        var method = Class.forName("dev.farskies.Campaign").getDeclaredMethod("deliver", ServerPlayer.class, CampaignData.class);
                        method.setAccessible(true);
                        for (int i = 0; i < n; i++) method.invoke(null, player(mc), CampaignData.get(server(mc)));
                    } catch (Exception e) { out("deliver failed " + e); }
                });
            }
            case "actor" -> actor(mc, a);
            case "unactor" -> {
                for (var key : a.length > 1 ? List.of(a[1]) : List.copyOf(ACTORS.keySet())) {
                    var p = ACTORS.remove(key); SWING.remove(key);
                    if (p != null) mc.level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
                }
            }
            case "ride" -> { // ride ACTOR entity-uuid-or-type-nearest
                var p = ACTORS.get(a[1]);
                var vehicle = mc.level.getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(16), e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(a[2])).stream().findFirst().orElse(null);
                if (vehicle == null) out("ride: no " + a[2]); else out("ride " + p.startRiding(vehicle, true));
            }
            case "find" -> find(mc, a);
            case "where" -> server(mc).execute(() -> {
                var data = CampaignData.get(server(mc));
                out("red " + WorldSetup.pos(data, 0).toShortString() + " blue " + WorldSetup.pos(data, 1).toShortString() + " market " + WorldSetup.pos(data, 2).toShortString() + " side " + data.account(player(mc).getUUID()).faction);
                if (data.eventPosition.length == 3) out("event " + WorldSetup.pos(data.eventPosition).toShortString());
            });
            case "top" -> server(mc).execute(() -> { int x = Integer.parseInt(a[1]), z = Integer.parseInt(a[2]); out("top " + x + " " + z + " = " + server(mc).overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)); });
            case "ray" -> server(mc).execute(() -> {
                var p = player(mc); var hit = p.pick(300, 0, false);
                out("ray " + (hit instanceof net.minecraft.world.phys.BlockHitResult b && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? b.getBlockPos().toShortString() + " " + server(mc).overworld().getBlockState(b.getBlockPos()).getBlock() : "miss"));
            });
            case "ents" -> {
                for (var e : mc.level.entitiesForRendering()) if (e.distanceTo(mc.player) < 64) out("ent " + BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) + " " + e.getClass().getSimpleName() + " " + e.blockPosition().toShortString());
            }
            case "hand" -> hideHand = a[1].equals("off");
            case "toasts" -> mc.getToasts().clear();
            case "lookevent" -> { // lookevent ox oy oz [ty]: camera at the sky cache + offset, looking at it
                double[] v = nums(a, 1, 3); double ty = a.length > 4 ? Double.parseDouble(a[4]) : 0.5;
                server(mc).execute(() -> {
                    var data = CampaignData.get(server(mc));
                    if (data.eventPosition.length != 3) { out("lookevent: no event"); return; }
                    var c = Vec3.atBottomCenterOf(WorldSetup.pos(data.eventPosition));
                    var eye = c.add(v[0], v[1], v[2]); float[] r = aim(eye, c.add(0, ty, 0));
                    var p = player(mc); p.setGameMode(GameType.SPECTATOR); p.teleportTo(p.serverLevel(), eye.x, eye.y - p.getEyeHeight(), eye.z, r[0], r[1]);
                    out("lookevent cache " + WorldSetup.pos(data.eventPosition).toShortString());
                });
            }
            case "echo" -> out(line.substring(5));
            case "quit" -> { out("COMPLETE"); stage = 5; mc.stop(); }
            default -> out("unknown command: " + line);
        }
    }

    private static void poll() {
        var queued = dir.resolve("queue.txt");
        try {
            if (Files.exists(queued)) {
                var lines = Files.readAllLines(queued, StandardCharsets.UTF_8);
                Files.delete(queued);
                QUEUE.addAll(lines);
                out("queued " + lines.size() + " lines");
            }
        } catch (Exception e) { out("queue read failed " + e); }
    }

    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); ticks++; mc.options.pauseOnLostFocus = false;
        if (mc.player != null) { mc.mouseHandler.releaseMouse(); net.minecraft.client.KeyMapping.releaseAll(); }
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen && stage >= 3) mc.setScreen(null);
        for (var entry : SWING.entrySet()) {
            var p = ACTORS.get(entry.getKey());
            if (p != null) { p.swinging = true; p.swingTime = 1; p.swingingArm = InteractionHand.MAIN_HAND; p.attackAnim = entry.getValue()[0]; p.oAttackAnim = entry.getValue()[0]; }
        }
        try {
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                dir = mc.gameDirectory.toPath().resolve("gallery");
                Files.createDirectories(dir);
                Files.deleteIfExists(dir.resolve("out.txt"));
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
                stage = 1; CreateWorldScreen.openFresh(mc, mc.screen);
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen screen) {
                var state = screen.getUiState();
                state.setName("RandomItemSkyblock-Gallery-" + RUN); state.setSeed(System.getProperty("farskies.gallerySeed", "5788880262892164030"));
                var create = screen.children().stream().filter(e -> e instanceof Button b && b.getMessage().getString().equals(Component.translatable("selectWorld.create").getString())).map(e -> (Button) e).findFirst().orElseThrow();
                stage = 2; ticks = 0; create.onPress();
            } else if (stage == 2 && mc.screen instanceof ConfirmScreen screen) {
                screen.children().stream().filter(e -> e instanceof Button b && b.getMessage().getString().matches("Proceed|Yes")).map(e -> (Button) e).findFirst().ifPresent(Button::onPress);
            } else if (stage == 2 && mc.player != null && mc.level != null && mc.screen == null && ticks > 100) {
                if (!CampaignData.get(server(mc)).initialized) throw new IllegalStateException("Campaign not initialized");
                mc.options.guiScale().set(3); mc.resizeDisplay();
                mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE; mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var iris = Class.forName("net.irisshaders.iris.Iris"); var config = iris.getMethod("getIrisConfig").invoke(null);
                if (!(boolean) config.getClass().getMethod("areShadersEnabled").invoke(config)) {
                    config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true); config.getClass().getMethod("save").invoke(config); iris.getMethod("reload").invoke(null);
                }
                for (var rule : List.of("fallDamage false", "doDaylightCycle false", "doWeatherCycle false", "doMobSpawning false", "doInsomnia false", "doPatrolSpawning false", "doTraderSpawning false", "sendCommandFeedback false"))
                    run(mc, "gamerule " + rule, false);
                run(mc, "weather clear", false);
                var script = System.getProperty("farskies.galleryScript");
                if (script != null) QUEUE.addAll(Files.readAllLines(Path.of(script), StandardCharsets.UTF_8));
                out("READY " + mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height + " script " + script);
                stage = 3; ticks = 0;
            } else if (stage == 3) {
                if (mc.player.isDeadOrDying()) mc.player.respawn();
                if (waitTicks > 0) { waitTicks--; return; }
                if (QUEUE.isEmpty() && ticks % 10 == 0) poll();
                while (!QUEUE.isEmpty() && waitTicks == 0 && stage == 3) {
                    var line = QUEUE.poll();
                    try { exec(mc, line); } catch (Exception e) { out("FAILED " + line + " : " + e); }
                }
            }
        } catch (Exception error) { FarSkies.LOG.error("GALLERY_QA FAILED", error); stage = 5; mc.stop(); }
    }
}
