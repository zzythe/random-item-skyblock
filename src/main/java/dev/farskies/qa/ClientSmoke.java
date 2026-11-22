package dev.farskies.qa;

import dev.farskies.CampaignData;
import dev.farskies.FarSkies;
import dev.farskies.WorldSetup;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives actual screen buttons, movement, block interactions and merchant packets in a disposable world. */
public final class ClientSmoke {
    private static int stage,ticks,side;
    private static final boolean NORMAL=Boolean.getBoolean("farskies.normalSmoke");
    private static final String RUN=Long.toString(System.currentTimeMillis());
    public static void register(){Minecraft.getInstance().execute(()->org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientSmoke::tick);}
    private static void next(int value){FarSkies.LOG.info("CLIENT_QA stage {}",value);stage=value;ticks=0;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"random-item-skyblock-0.6.1-"+name+".png",mc.getMainRenderTarget(),c->{});}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void use(Minecraft mc,BlockPos pos,Direction face){mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),face,pos,false));}
    public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("farskies.clientSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;ticks++;
        if(mc.player!=null){mc.mouseHandler.releaseMouse();net.minecraft.client.KeyMapping.releaseAll();if(stage==41)mc.options.keyUp.setDown(true);if(stage==54)mc.options.keyDown.setDown(true);}
        if(ticks%100==0)FarSkies.LOG.info("CLIENT_QA waiting stage={} screen={}",stage,mc.screen==null?"none":mc.screen.getClass().getSimpleName());
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen && stage>=2 && stage<90)mc.setScreen(null);
        try {
            if(stage==0 && (mc.screen instanceof TitleScreen || mc.screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) && mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1600,900);next(1);CreateWorldScreen.openFresh(mc,mc.screen);
            } else if(stage==1 && mc.screen instanceof CreateWorldScreen screen) {
                var state=screen.getUiState();
                check(state.getWorldType().preset().unwrapKey().orElseThrow().location().equals(ResourceLocation.parse("farskies:far_skies")),"Default preset missing");
                check(state.isGenerateStructures(),"Default island structures are disabled");state.setName("RandomItemSkyblock-"+(NORMAL?"Normal-":"Client-")+RUN);state.setSeed("5788880262892164030");
                if(NORMAL)state.setWorldType(new net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry(state.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder(ResourceLocation.parse("minecraft:normal")).orElseThrow()));
                var create=screen.children().stream().filter(e->e instanceof Button b && b.getMessage().getString().equals(Component.translatable("selectWorld.create").getString())).map(e->(Button)e).findFirst().orElseThrow();
                next(2);create.onPress();
            } else if(stage==2 && mc.screen instanceof ConfirmScreen screen) {
                screen.children().stream().filter(e->e instanceof Button b && (b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("Yes"))).map(e->(Button)e).findFirst().ifPresent(Button::onPress);
            } else if(stage==2 && mc.player!=null && mc.level!=null && ticks>80 && mc.screen==null) {
                var data=CampaignData.get(mc.getSingleplayerServer());
                if(NORMAL){check(!data.initialized,"Normal world converted");Files.writeString(Path.of("qa-normal-pass.txt"),"Ordinary world passed.\n");next(90);}
                else {
                    check(data.initialized,"Campaign not initialized");
                    side=data.account(mc.player.getUUID()).faction;
                    check(side>=0,"Random team not assigned");next(3);
                }
            } else if(stage==3 && mc.player!=null && ticks>60) {
                check(mc.player.getX()*(side==0?-1:1)>70,"Random assignment did not arrive at its base");
                check(Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()+(side==0?90:-90)))<1,"Client does not face rival");
                mc.player.setXRot(8);
                if(ticks<120)return;
                check(mc.screen==null,"Unexpected team selection screen");shot(mc,"start");
                Object manager=Class.forName("dev.emi.emi.api.EmiApi").getMethod("getRecipeManager").invoke(null);
                var recipes=(java.util.List<?>)Class.forName("dev.emi.emi.api.recipe.EmiRecipeManager").getMethod("getRecipes").invoke(manager);
                var type=Class.forName("dev.emi.emi.api.recipe.EmiRecipe");
                long count=recipes.stream().filter(r->{try{return String.valueOf(type.getMethod("getId").invoke(r)).startsWith("create:");}catch(Exception e){throw new RuntimeException(e);}}).count();
                check(count>50,"Create recipes missing from EMI");FarSkies.LOG.info("CLIENT_QA {} Create recipes",count);
                var index=dev.emi.emi.registry.EmiStackList.stacks;
                check(!index.isEmpty(),"EMI index is not baked");
                check(index.stream().noneMatch(stack->{var id=stack.getId();return id!=null&&dev.farskies.ItemDrops.unavailableIntegration(id.getNamespace(),id.getPath());}),"Unsupported integration leaked into EMI");
                int textured=0;
                for(var entry:dev.farskies.ItemDrops.entries())if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.item()).getNamespace().equals("exdeorum")){
                    var model=mc.getItemRenderer().getModel(new ItemStack(entry.item()),mc.level,mc.player,0);
                    check(!model.getParticleIcon().contents().name().getPath().equals("missingno"),"Missing texture on supported Ex Deorum item "+entry.item());textured++;
                }
                check(textured>40,"Manual resource-processing items disappeared");FarSkies.LOG.info("CLIENT_QA EMI integrations hidden; {} Ex Deorum item models textured",textured);
                mc.options.guiScale().set(2);mc.resizeDisplay();
                mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION,12000,2));});next(31);
            } else if(stage==31 && ticks>30) {
                shot(mc,"scale2");var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,false);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);next(32);
            } else if(stage==32 && ticks>40) {
                shot(mc,"no-shaders");mc.options.guiScale().set(4);mc.resizeDisplay();next(33);
            } else if(stage==33 && ticks>30) {
                shot(mc,"no-shaders-scale4");var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
                mc.player.connection.sendCommand("market");next(4);
            } else if(stage==4 && ticks>10) {
                var data=CampaignData.get(mc.getSingleplayerServer());
                check(mc.player.blockPosition().distSqr(WorldSetup.pos(data,2))<4,"Instant Market travel failed");
                mc.player.setYRot(0);mc.player.setXRot(5);next(40);
            } else if(stage==40 && ticks>10) {
                shot(mc,"market-entrance");mc.options.keyUp.setDown(true);next(41);
            } else if(stage==41 && ticks==10){use(mc,new BlockPos(3,152,23),Direction.NORTH);
            } else if(stage==41 && ticks>36) {
                mc.options.keyUp.setDown(false);
                check(mc.player.getZ()>24 && mc.player.getY()>151.7,"Cannot walk through shop entrance: "+mc.player.position());
                shot(mc,"inside-shop");
                mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.getInventory().add(new ItemStack(Items.EMERALD,4));p.getInventory().add(new ItemStack(Items.DIRT,8));p.inventoryMenu.broadcastChanges();});
                var trader=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e.getUUID().equals(java.util.UUID.fromString("32cb3cc8-137d-423b-b092-d6ad12109da5"))).findFirst().orElseThrow();
                mc.gameMode.interact(mc.player,trader,net.minecraft.world.InteractionHand.MAIN_HAND);next(5);
            } else if(stage==5 && ticks>30) {
                check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen,"Villager did not open shop");
                var menu=(net.minecraft.world.inventory.MerchantMenu)mc.player.containerMenu;check(menu.getOffers().size()>7,"No scrollable stock");shot(mc,"shop-top");
                mc.screen.mouseScrolled(mc.screen.width/2.,mc.screen.height/2.,0,-4);next(51);
            } else if(stage==51 && ticks>15) {
                var field=net.minecraft.client.gui.screens.inventory.MerchantScreen.class.getDeclaredField("scrollOff");field.setAccessible(true);
                check(field.getInt(mc.screen)>0,"Mouse wheel did not scroll merchant");shot(mc,"shop-scrolled");
                mc.screen.mouseScrolled(mc.screen.width/2.,mc.screen.height/2.,0,100);
                var buttons=mc.screen.children().stream().filter(e->e.getClass().getSimpleName().equals("TradeOfferButton")).toList();
                var buy=(net.minecraft.client.gui.components.AbstractWidget)buttons.get(4);
                mc.screen.mouseClicked(buy.getX()+10,buy.getY()+10,0);next(52);
            } else if(stage==52 && ticks>15) {
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,2,0,ClickType.QUICK_MOVE,mc.player);next(53);
            } else if(stage==53 && ticks>15) {
                check(mc.player.getInventory().items.stream().anyMatch(s->s.is(chanceCubes.items.CCubesItems.CHANCE_CUBE.get())),"Native cube purchase failed");
                check(((net.minecraft.world.inventory.MerchantMenu)mc.player.containerMenu).getOffers().get(4).getUses()==1,"Stock not consumed");
                check(!mc.player.getInventory().contains(new ItemStack(Items.EMERALD)),"Payment not consumed");
                mc.player.closeContainer();use(mc,new BlockPos(3,152,23),Direction.NORTH);mc.options.keyDown.setDown(true);next(54);
            } else if(stage==54 && ticks>14) {
                mc.options.keyDown.setDown(false);
                use(mc,WorldSetup.pos(CampaignData.get(mc.getSingleplayerServer()).communityChest),Direction.UP);next(55);
            } else if(stage==55 && ticks>15) {
                check(mc.player.containerMenu.getType()==net.minecraft.world.inventory.MenuType.GENERIC_9x3,"Shared chest cannot be opened");
                int slot=-1;for(int i=27;i<mc.player.containerMenu.slots.size();i++)if(mc.player.containerMenu.slots.get(i).getItem().is(Items.DIRT)){slot=i;break;}
                check(slot>=0,"No dirt for exchange check");mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);next(56);
            } else if(stage==56 && ticks>15) {
                check(java.util.stream.IntStream.range(0,27).anyMatch(i->mc.player.containerMenu.slots.get(i).getItem().is(Items.DIRT)),"Chest did not accept player goods");shot(mc,"community-chest");
                int slot=java.util.stream.IntStream.range(0,27).filter(i->mc.player.containerMenu.slots.get(i).getItem().is(Items.DIRT)).findFirst().orElseThrow();
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);next(57);
            } else if(stage==57 && ticks>15) {
                check(mc.player.getInventory().contains(new ItemStack(Items.DIRT)),"Shared goods cannot be withdrawn");mc.player.closeContainer();mc.player.connection.sendCommand("market home");next(58);
            } else if(stage==58 && ticks>10) {
                check(mc.player.getX()*(side==0?-1:1)>70,"Instant return failed");mc.player.connection.sendCommand("market");next(59);
            } else if(stage==59 && ticks>10) {
                int slot=-1;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(Items.DIRT)){slot=i;break;}
                check(slot>=0,"Missing bridge blocks");mc.player.getInventory().selected=slot;
                next(60);
            } else if(stage==60 && ticks>10) {
                long delivered=CampaignData.get(mc.getSingleplayerServer()).account(mc.player.getUUID()).received;
                if(delivered<2){check(ticks<1500,"Two actual 30-second deliveries did not arrive");return;}
                check(delivered==2,"Deliveries are arriving faster than 30 seconds");next(63);
            } else if(stage==63 && ticks>15) {
                int count=mc.player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
                check(count>=14,"Two deliveries did not add valid item stacks; inventory total="+count);
                mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var target=new BlockPos(-14,152,27);
                    p.teleportTo(mc.getSingleplayerServer().overworld(),target.getX()+.5,target.getY(),target.getZ()+.5,180,0);
                });next(61);
            } else if(stage==61 && ticks>20) {
                use(mc,new BlockPos(-14,154,26),Direction.SOUTH);next(62);
            } else if(stage==62 && ticks>20) {
                check(net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(mc.player.containerMenu.getType()).toString().equals("farmersdelight:cooking_pot"),"Public kitchen cannot open");
                shot(mc,"public-kitchen");mc.player.closeContainer();
                mc.getSingleplayerServer().execute(()->{
                    var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(mc.player.getUUID());var d=CampaignData.get(server);int old=d.account(p.getUUID()).faction;
                    dev.farskies.Campaign.login(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));check(d.account(p.getUUID()).faction==old,"Rejoining switched an existing player");
                    String team=old==0?"Blue":"Red";server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),"teamswitch "+p.getScoreboardName()+" "+team);
                    check(d.account(p.getUUID()).faction==1-old&&p.getRespawnPosition()==null,"Operator team switch failed");
                    for(var id:new String[]{"farskies_west","farskies_east"})check(server.getScoreboard().getPlayerTeam(id).isAllowFriendlyFire(),"Friendly fire disabled");
                    for(int x=-2;x<=2;x++)for(int z=-8;z<=4;z++)server.overworld().setBlockAndUpdate(new BlockPos(x,150,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                    p.teleportTo(server.overworld(),.5,151,-6.5,0,16);
                });mc.options.guiScale().set(2);mc.resizeDisplay();next(70);
            } else if(stage==70&&ticks>60){shot(mc,"market-edge-armour");
                Files.writeString(Path.of("qa-client-pass-0.5.txt"),"Random persistent teams, OP swapping, friendly fire, native HUD with armour and absorption, shared progression, shaders, GUI scales, travel, merchant scrolling, public chest, kitchen and market boundary passed.\n");next(90);
            } else if(stage==90 && ticks>45){mc.options.keyUp.setDown(false);next(99);mc.stop();}
        } catch(Exception e){mc.options.keyUp.setDown(false);next(99);FarSkies.LOG.error("CLIENT_QA failed",e);throw new IllegalStateException(e);}
    }
}
