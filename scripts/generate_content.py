"""Generate JSON content only. All player-facing structures come from world-content mods."""
from pathlib import Path
import json
import zipfile

ROOT = Path('src/main/resources')


def write(path, data):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def main():
    names = [f'Tier {i}' for i in range(1,7)]
    for i, name in enumerate(names):
        display = {'icon': {'id': ['minecraft:oak_sapling', 'minecraft:wheat', 'create:cogwheel', 'create:precision_mechanism', 'minecraft:diamond', 'minecraft:netherite_scrap'][i]},
                   'title': {'text': name}, 'description': {'text': f'Tier {i + 1} items can now arrive in your random drops.'}, 'frame': 'task', 'show_toast': True, 'announce_to_chat': False}
        if i == 0:
            display['background'] = 'minecraft:textures/block/oak_planks.png'
        data = {'display': display, 'criteria': {'supply': {'trigger': 'minecraft:impossible'}}}
        if i:
            data['parent'] = f'farskies:era_{i - 1}'
        write(f'data/farskies/advancement/era_{i}.json', data)

    events = []

    def event(id, title, grade, kind='items', tier=0, items=(), **extra):
        events.append(dict(id=id, title=title, grade=grade, kind=kind, minTier=tier,
                           items=[{'item': item if ':' in item else 'minecraft:' + item, 'count': count} for item, count in items],
                           entity=extra.get('entity', ''), effect=extra.get('effect', ''), count=extra.get('count', 1),
                           enchantment=extra.get('enchantment', ''), enchantLevel=extra.get('enchantLevel', 1)))

    event('rescue_raft', 'Emergency raft. Some assembly required.', 'good', items=[('oak_planks', 48), ('water_bucket', 1), ('bread', 8)])
    event('seed_archive', 'The seed archive survived the fall.', 'good', items=[('wheat_seeds', 12), ('pumpkin_seeds', 8), ('melon_seeds', 8), ('carrot', 8), ('sugar_cane', 8)])
    event('geologist', 'A geologist packed this one.', 'good', items=[('andesite', 32), ('iron_ingot', 12), ('copper_ingot', 16)])
    event('windstep', 'Windstep boots. Check the landing before jumping.', 'good', 'enchant', entity='minecraft:iron_boots', enchantment='minecraft:feather_falling', enchantLevel=4)
    event('lumberjack', 'One very compact forest.', 'good', items=[('oak_log', 32), ('spruce_log', 32), ('bone_meal', 16)])
    event('generator_rescue', 'The generator repair kit.', 'good', items=[('lava_bucket', 1), ('water_bucket', 2), ('iron_ingot', 8), ('dirt', 16)])
    event('mechanic_drawer', 'Someone left the workshop drawer unlocked.', 'good',  tier=1, items=[('create:andesite_alloy', 12), ('create:shaft', 8), ('create:cogwheel', 8), ('create:water_wheel', 1)])
    event('quiet_pick', 'The quiet pick.', 'good', 'enchant', tier=1, entity='minecraft:iron_pickaxe', enchantment='minecraft:silk_touch', enchantLevel=1)
    event('tinker', 'Factory salvage. Build something unreasonable.', 'good', tier=2, items=[('create:brass_ingot', 16), ('create:electron_tube', 4), ('create:precision_mechanism', 2)])
    event('prospector', 'A prospector never came back for this.', 'good', tier=2, items=[('diamond', 4), ('gold_ingot', 16), ('lapis_lazuli', 24)])
    event('beekeeper', 'The beekeeper has retired.', 'good', tier=1, items=[('honey_block', 12), ('honeycomb', 16), ('flowering_azalea', 4)])
    event('pocket_nether', 'The Nether, neatly packed.', 'good', tier=3, items=[('nether_wart', 8), ('soul_sand', 16), ('blaze_rod', 6), ('glowstone', 16)])
    event('horizon', 'A message from beyond the horizon.', 'good', tier=4, items=[('shulker_shell', 2), ('end_stone', 32), ('ender_pearl', 8)])
    event('old_king', 'The old king packed light.', 'good', tier=5, items=[('netherite_scrap', 4), ('totem_of_undying', 1), ('experience_bottle', 24)])
    event('bargain_gold', 'Budget jackpot.', 'mid', items=[('gold_nugget', 9), ('potato', 1)])
    event('glass_rain', 'Glass rain. Thankfully it arrived boxed.', 'mid', items=[('glass', 32), ('light_blue_stained_glass', 16)])
    event('potato', 'It is a very important potato.', 'mid', items=[('potato', 1)])
    event('chicken_council', 'The chicken council has arrived.', 'mid', 'mob', entity='minecraft:chicken', count=6, items=[('egg', 6)])
    event('clerical_error', 'A clerical error in your favour. Mostly paper.', 'mid', items=[('paper', 32), ('ink_sac', 4)])
    event('desert_mail', 'A postcard from the desert.', 'mid', items=[('sand', 24), ('cactus', 3), ('dead_bush', 2)])
    event('gardener', 'A gardener mistook this for a flowerpot.', 'mid', items=[('flower_pot', 6), ('poppy', 6), ('dandelion', 6), ('moss_carpet', 12)])
    event('wet_socks', 'Wet socks, dry provisions.', 'mid', items=[('leather_boots', 1), ('dried_kelp', 16)])
    event('uninvited', 'Uninvited guests.', 'bad', 'mob', entity='minecraft:zombie', count=4)
    event('silverfish_union', 'The silverfish union objects.', 'bad', 'mob', entity='minecraft:silverfish', count=6)
    event('sapper', 'That ticking is probably fine. Five seconds.', 'bad', 'tnt')
    event('creeper_tax', 'The creeper tax is due.', 'bad', 'mob', entity='minecraft:creeper', count=1)
    event('borrowed_gravity', 'Gravity has been temporarily reassigned.', 'bad', 'effect', effect='minecraft:levitation', count=1, items=[('feather', 1)])
    event('contaminated', 'Contaminated ore. Do not lick it.', 'bad', 'effect', effect='minecraft:poison', count=1, items=[('raw_gold', 8)])
    event('lights_out', 'Lights out.', 'bad', 'effect', effect='minecraft:blindness', count=1, items=[('torch', 8)])
    event('debt_collector', 'The debt collector carries eight gold. Collect it.', 'bad', 'mob', entity='minecraft:husk', count=1)
    event('spider_party', 'The web committee.', 'bad', 'mob', entity='minecraft:spider', count=3, items=[('string', 8)])
    event('hunger', 'A feast for somebody else.', 'bad', 'effect', effect='minecraft:hunger', count=2, items=[('rotten_flesh', 16)])
    write('farskies-luck.json', events)
    # Vanilla void; all exploration geometry comes from installed template mods.
    sky_biome = {'has_precipitation': False, 'temperature': 0.8, 'downfall': 0.4,
                 'effects': {'fog_color':12638463,'water_color':4159204,'water_fog_color':329011,'sky_color':7907327},
                 'spawners':{},'spawn_costs':{},'carvers':{},'features':[]}
    # Keep vanilla plains spawning in the surrounding void so player-built farms work.
    def spawn(name,weight,lo,hi):
        return {'type':'minecraft:'+name,'weight':weight,'minCount':lo,'maxCount':hi}
    sky_biome['spawners']={
        'ambient':[spawn('bat',10,8,8)],
        'creature':[spawn('sheep',12,4,4),spawn('pig',10,4,4),spawn('chicken',10,4,4),spawn('cow',8,4,4),spawn('horse',5,2,6),spawn('donkey',1,1,3)],
        'monster':[spawn('spider',100,4,4),spawn('zombie',95,4,4),spawn('zombie_villager',5,1,1),spawn('skeleton',100,4,4),spawn('creeper',100,4,4),spawn('slime',100,4,4),spawn('enderman',10,1,4),spawn('witch',5,1,1)],
        'underground_water_creature':[spawn('glow_squid',10,4,6)]}
    write('data/farskies/worldgen/biome/open_sky.json', sky_biome)
    overworld = {'type':'minecraft:overworld','generator':{'type':'minecraft:flat','settings':{
        'layers':[], 'biome':'farskies:open_sky','features':False,'lakes':False,'structure_overrides':['farskies:islands']}}}
    dimensions = {'minecraft:overworld':overworld,
        'minecraft:the_nether':{'type':'minecraft:the_nether','generator':{'type':'minecraft:noise','settings':'minecraft:nether','biome_source':{'type':'minecraft:multi_noise','preset':'minecraft:nether'}}},
        'minecraft:the_end':{'type':'minecraft:the_end','generator':{'type':'minecraft:noise','settings':'minecraft:end','biome_source':{'type':'minecraft:the_end'}}}}
    island_biomes=['badlands','basalt_deltas','birch_forest','cherry_grove','crimson_forest','dark_forest','deep_dark','desert','dripstone_caves','end_highlands','ice_spikes','jungle','lush_caves','mangrove_swamp','mushroom_fields','savanna','soul_sand_valley','sunflower_plains','taiga','warped_forest']
    structures=[]
    for name in island_biomes:
        barren=name in ['basalt_deltas','end_highlands','desert']
        write(f'data/farskies/worldgen/structure/{name}.json',{'type':'farskies:island','biomes':['farskies:open_sky'],'step':'top_layer_modification','spawn_overrides':{},'pool':'skyvoid_biome_islands:'+name,'island_biome':'minecraft:'+name,'loot':'none' if barren else 'supplies','terrain_adaptation':'none'})
        structures.append({'structure':'farskies:'+name,'weight':3 if name in ['birch_forest','savanna','taiga','jungle','sunflower_plains'] else 2})
    for name,biome in [('forest_camp','forest'),('oasis','desert'),('tundra','snowy_plains'),('igloo','snowy_plains'),('mesa_mine','badlands'),('ruined_portal','forest')]:
        write(f'data/farskies/worldgen/template_pool/{name}.json',{'fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','projection':'rigid','location':'robs_floating_islands:'+name,'processors':'minecraft:empty'}}]})
        write(f'data/farskies/worldgen/structure/{name}.json',{'type':'farskies:island','biomes':['farskies:open_sky'],'step':'top_layer_modification','spawn_overrides':{},'pool':'farskies:'+name,'island_biome':'minecraft:'+biome,'loot':'treasure','terrain_adaptation':'none'})
        structures.append({'structure':'farskies:'+name,'weight':1})
    write('data/farskies/worldgen/structure_set/islands.json',{'structures':structures,'placement':{'type':'minecraft:random_spread','spacing':5,'separation':2,'salt':73195421,'spread_type':'linear'}})
    write('data/farskies/tags/worldgen/structure/islands.json',{'values':[s['structure'] for s in structures]})
    write('data/farskies/worldgen/world_preset/far_skies.json', {'dimensions':dimensions})
    write('data/minecraft/tags/worldgen/world_preset/normal.json', {'replace':False,'values':['farskies:far_skies']})
    write('assets/farskies/lang/en_us.json', {'generator.farskies.far_skies':'Random Item Skyblock'})
    rewards = [('minecraft:iron_ingot',30,2,4),('create:andesite_alloy',25,2,5),('chancecubes:chance_cube',15,1,1),
               ('minecraft:golden_apple',8,1,1),('minecraft:diamond',4,1,1),('minecraft:cobweb',18,1,2)]
    entries = [{'type':'minecraft:item','name':name,'weight':weight,'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':lo,'max':hi}}]} for name,weight,lo,hi in rewards]
    write('data/farskies/loot_table/island/treasure.json', {'type':'minecraft:chest','pools':[{'rolls':1,'entries':entries}]})
    def loot_entry(name,weight,lo,hi):
        return {'type':'minecraft:item','name':name,'weight':weight,'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':lo,'max':hi}}]}
    building=[loot_entry('minecraft:oak_planks',4,20,32),loot_entry('minecraft:cobblestone',4,24,40),loot_entry('minecraft:bread',2,4,8)]
    useful=[loot_entry('minecraft:iron_ingot',4,3,6),loot_entry('create:andesite_alloy',3,3,6),loot_entry('create:cogwheel',2,2,4),loot_entry('minecraft:arrow',2,12,24)]
    write('data/farskies/loot_table/island/supplies.json',{'type':'minecraft:chest','pools':[{'rolls':1,'entries':building},{'rolls':1,'entries':useful}]})
    contested=[loot_entry('minecraft:iron_ingot',1,6,8),loot_entry('chancecubes:chance_cube',1,1,1),loot_entry('create:andesite_alloy',1,6,10)]
    write('data/farskies/loot_table/island/contested.json',{'type':'minecraft:chest','pools':[{'rolls':1,'entries':[entry]} for entry in contested]})
    # Cubes are earned at the market. Disable the base mod's cheap crafting shortcuts.
    for recipe in ['chance_cube_crafting', 'tier_1_pendant_crafting', 'tier_2_pendant_crafting', 'tier_3_pendant_crafting']:
        write(f'data/chancecubes/recipe/{recipe}.json', {'neoforge:conditions': [{'type': 'neoforge:false'}], 'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:bedrock'}], 'result': {'id': 'minecraft:barrier', 'count': 1}})
    write('data/farskies/recipe/cursed_cube.json', {'type': 'minecraft:crafting_shapeless',
        'ingredients': [{'item': 'chancecubes:chance_cube'}, {'item': 'minecraft:spider_eye'}, {'item': 'minecraft:gunpowder'}],
        'result': {'id': 'chancecubes:chance_cube', 'count': 1, 'components': {'chancecubes:chance': -75}}})
    from generate_playtest_content import main as playtest_content
    playtest_content()
    print(f'Generated {len(events)} lucky outcomes and six native advancements.')


if __name__ == '__main__':
    main()
