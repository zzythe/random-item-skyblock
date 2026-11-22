"""Data for the shared survival, exploration and competition loop. No authored geometry."""
import json
from pathlib import Path
from generate_content import write


def item(name, lo=1, hi=None, weight=1):
    return {'type':'minecraft:item','name':name if ':' in name else 'minecraft:'+name,'weight':weight,
            'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':lo,'max':hi or lo}}]}


def table(*pools):
    return {'type':'minecraft:chest','pools':[{'rolls':1,'entries':pool} for pool in pools]}


def main():
    themes = {
        'badlands':[item('raw_gold',8,12),item('gold_ingot',6,10)],
        'basalt_deltas':[item('lava_bucket'),item('quartz',12,20)],
        'birch_forest':[item('iron_ingot',8,12),item('exdeorum:flint_mesh')],
        'cherry_grove':[item('kaleidoscope_cookery:straw_hat'),item('kaleidoscope_cookery:tea_seed',4,8)],
        'crimson_forest':[item('blaze_rod',2,3),item('nether_wart',6,10)],
        'dark_forest':[item('lead',2),item('cow_spawn_egg',2)],
        'deep_dark':[item('echo_shard',2,4),item('diamond',2,3)],
        'desert':[item('lapis_lazuli',16,24),item('gold_ingot',8,12)],
        'dripstone_caves':[item('lava_bucket'),item('pointed_dripstone',8,12)],
        'end_highlands':[item('ender_pearl',4,6),item('shulker_shell')],
        'ice_spikes':[item('packed_ice',16,24),item('fishing_rod')],
        'jungle':[item('kaleidoscope_cookery:rice',6,10),item('cocoa_beans',12,16)],
        'lush_caves':[item('kaleidoscope_cookery:lettuce_seed',6,10),item('glow_berries',8,12)],
        'mangrove_swamp':[item('slime_ball',12,20),item('lily_pad',8,12)],
        'mushroom_fields':[item('mooshroom_spawn_egg'),item('mycelium',8,12)],
        'savanna':[item('leather',12,16),item('book',6,10)],
        'soul_sand_valley':[item('ghast_tear',1,2),item('soul_sand',12,20)],
        'sunflower_plains':[item('kaleidoscope_cookery:tomato_seed',6,10),item('kaleidoscope_cookery:chili_seed',6,10)],
        'taiga':[item('wolf_spawn_egg',2),item('bone',12,16)],
        'warped_forest':[item('ender_pearl',4,6),item('warped_fungus',2,4)],
    }
    building=[item('oak_planks',24,40),item('cobblestone',32,48)]
    useful=[item('iron_ingot',6,10),item('create:andesite_alloy',6,10),item('emerald',3,5)]
    food=[item('bread',6,10),item('kaleidoscope_cookery:egg_fried_rice',2,3)]
    for name,identity in themes.items():
        write('data/farskies/loot_table/island/theme/'+name+'.json',table(building,identity,useful,food))
        p=Path('src/main/resources/data/farskies/worldgen/structure')/(name+'.json')
        data=json.loads(p.read_text());data['loot']='theme/'+name;write(p.relative_to('src/main/resources'),data)
    for name,theme in [('forest_camp','birch_forest'),('oasis','desert'),('tundra','ice_spikes'),('igloo','ice_spikes'),('mesa_mine','badlands'),('ruined_portal','crimson_forest')]:
        p=Path('src/main/resources/data/farskies/worldgen/structure')/(name+'.json');data=json.loads(p.read_text());data['loot']='theme/'+theme;write(p.relative_to('src/main/resources'),data)
    # Existing authored landmarks, using their own pools, processors and jigsaw pieces.
    landmarks=[]
    for name,biome,theme in [('camp_island','forest','birch_forest'),('tower_island','plains','deep_dark'),('pillager_camp','plains','savanna'),('cherry_island_with_shrine','cherry_grove','cherry_grove'),('cemetery_island','dark_forest','deep_dark'),('steampunk_island','windswept_hills','badlands')]:
        write(f'data/farskies/worldgen/structure/landmark_{name}.json',{'type':'farskies:island','biomes':['farskies:open_sky'],'step':'top_layer_modification','spawn_overrides':{},'pool':'floating_islands:'+name+'/start_pool','island_biome':'minecraft:'+biome,'loot':'theme/'+theme,'terrain_adaptation':'none'})
        landmarks.append({'structure':'farskies:landmark_'+name,'weight':1})
    write('data/farskies/tags/worldgen/structure/landmarks.json',{'values':[entry['structure'] for entry in landmarks]})
    write('data/farskies/tags/worldgen/structure/grand_islands.json',{'values':['farskies:landmark_cherry_island_with_shrine','farskies:landmark_cemetery_island']})
    write('data/farskies/worldgen/structure_set/landmarks.json',{'structures':landmarks,'placement':{'type':'minecraft:random_spread','spacing':13,'separation':6,'salt':62104829,'spread_type':'linear'}})
    preset=Path('src/main/resources/data/farskies/worldgen/world_preset/far_skies.json');data=json.loads(preset.read_text());data['dimensions']['minecraft:overworld']['generator']['settings']['structure_overrides']=['farskies:islands','farskies:landmarks'];write(preset.relative_to('src/main/resources'),data)
    tag=Path('src/main/resources/data/farskies/tags/worldgen/structure/islands.json');data=json.loads(tag.read_text());data['values'] += [entry['structure'] for entry in landmarks];write(tag.relative_to('src/main/resources'),data)
    # Improve legacy unopened cache tables too; already opened player chests are never refilled.
    write('data/farskies/loot_table/island/treasure.json',table(building,useful,[item('diamond',2,4),item('chancecubes:chance_cube')],food))
    write('data/farskies/loot_table/island/supplies.json',table(building,useful,food))
    for tier in range(6):
        material=item(['iron_ingot','gold_ingot','create:brass_ingot','diamond','diamond','netherite_scrap'][tier],4 if tier<3 else 2,8 if tier<3 else 3)
        write(f'data/farskies/loot_table/island/contest_{tier}.json',table([item('emerald',8,12)],[material],[item('golden_apple',2,3),item('chancecubes:chance_cube',2)], [item('arrow',24,32),item('experience_bottle',12,20)]))
    variants=[
        [item('ender_pearl',4,6),item('scaffolding',32,48)],
        [item('cow_spawn_egg',2),item('kaleidoscope_cookery:straw_hat')],
        [item('shield'),item('iron_chestplate')],
        [item('create:mechanical_press'),item('create:andesite_alloy',8,12)],
        [item('diamond',2,3),item('raw_gold',8,12)],
        [item('bow'),item('wind_charge',8,12)],
        [item('brewing_stand'),item('blaze_rod',2,3)],
        [item('chancecubes:chance_cube',2,3)],
    ]
    for tier in range(6):
        base=json.loads(Path(f'src/main/resources/data/farskies/loot_table/island/contest_{tier}.json').read_text())
        for index,kit in enumerate(variants):
            data=json.loads(json.dumps(base));data['pools'] += [{'rolls':1,'entries':[reward]} for reward in kit]
            write(f'data/farskies/loot_table/island/contest_{tier}_{index}.json',data)
    write('data/farskies/advancement/journey_cache.json',{'parent':'farskies:journey','display':{'icon':{'id':'minecraft:barrel'},'title':{'text':'First to the spoils'},'description':{'text':'Claim a contested sky cache.'},'show_toast':True,'announce_to_chat':False},'criteria':{'supply':{'trigger':'minecraft:impossible'}}})
    root={'display':{'icon':{'id':'minecraft:grass_block'},'title':{'text':'Random Item Skyblock'},'description':{'text':'Make resources, grow a home and explore. These projects are optional; recipes are always open.'},'background':'minecraft:textures/block/oak_planks.png','show_toast':False,'announce_to_chat':False},'criteria':{'supply':{'trigger':'minecraft:impossible'}}}
    write('data/farskies/advancement/journey.json',root)
    milestones=[
        ('generator','cobblestone','A steady supply','Make a cobblestone generator. Water and lava are in your starting supplies.',['minecraft:cobblestone'],16),
        ('sifting','exdeorum:oak_sieve','From gravel to metal','Hammer cobble into gravel, then use a sieve and mesh. EMI shows the recipes.',['exdeorum:oak_sieve'],1),
        ('metal','iron_ingot','More than scraps','Turn sifted ore chunks into metal, or find supplies on islands.',['minecraft:iron_ingot'],4),
        ('farming','kaleidoscope_cookery:straw_hat','Something growing','Wear the straw hat and break grass for crop seeds.',['kaleidoscope_cookery:straw_hat'],1),
        ('cooking','kaleidoscope_cookery:egg_fried_rice','Dinner is ready','Grow ingredients and cook. Check uses in EMI for your crops.',['kaleidoscope_cookery:egg_fried_rice','farmersdelight:vegetable_soup'],1),
        ('workshop','create:water_wheel','Let it work for you','Build a water wheel. Hold W over Create parts for Ponder.',['create:water_wheel'],1),
        ('diamond','diamond','A little wealth','Explore, sift or trade toward better equipment.',['minecraft:diamond'],1),
        ('beacon','beacon','A place to call home','Build a working beacon for your island.',['minecraft:beacon'],1),
    ]
    for name,icon,title,description,items,count in milestones:
        write('data/farskies/advancement/journey_'+name+'.json',{'parent':'farskies:journey','display':{'icon':{'id':icon if ':' in icon else 'minecraft:'+icon},'title':{'text':title},'description':{'text':description},'show_toast':True,'announce_to_chat':False},'criteria':{'supplies':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':items,'count':{'min':count}}]}}}})
    # Branches show what a project enables, without rewards, claims, or recipe gates.
    for child,parent in [('sifting','generator'),('metal','sifting'),('diamond','metal'),('cooking','farming'),('beacon','diamond')]:
        p=Path('src/main/resources/data/farskies/advancement/journey_'+child+'.json');data=json.loads(p.read_text());data['parent']='farskies:journey_'+parent;write(p.relative_to('src/main/resources'),data)
    projects=[
        ('mesh','sifting','exdeorum:flint_mesh','Choose what to make','Craft a flint mesh. In EMI, check which sieve inputs produce the metal you need.','exdeorum:flint_mesh'),
        ('kitchen','farming','farmersdelight:cooking_pot','Your own kitchen','Craft a cooking pot. Heat it over a campfire and add crops; sell spare meals at the market.','farmersdelight:cooking_pot'),
        ('power','workshop','create:water_wheel','Put water to work','Craft a water wheel. Flowing water turns it; shafts carry rotation. Hold W for Ponder.','create:crafting/kinetics/water_wheel'),
        ('press','power','create:mechanical_press','A working workshop','Craft a mechanical press. Connect rotation, then process ingots on a depot or belt.','create:crafting/kinetics/mechanical_press'),
        ('steam','press','create:steam_engine','Room to grow','Craft a steam engine. A boiler needs tanks, heat and water. Ponder shows the complete setup.','create:crafting/kinetics/steam_engine'),
    ]
    for name,parent,icon,title,description,recipe in projects:
        write('data/farskies/advancement/journey_'+name+'.json',{'parent':'farskies:journey_'+parent,'display':{'icon':{'id':icon},'title':{'text':title},'description':{'text':description},'show_toast':True,'announce_to_chat':False},'criteria':{'crafted':{'trigger':'minecraft:recipe_crafted','conditions':{'recipe_id':recipe}}}})
    p=Path('src/main/resources/data/farskies/advancement/journey_beacon.json');data=json.loads(p.read_text());data['criteria']={'built':{'trigger':'minecraft:construct_beacon','conditions':{'level':{'min':1}}}};write(p.relative_to('src/main/resources'),data)
    p=Path('src/main/resources/data/farskies/advancement/era_0.json');data=json.loads(p.read_text());data['parent']='farskies:journey';data['display'].pop('background',None);write(p.relative_to('src/main/resources'),data)
    # The upstream cooking jar lists optional Quark furniture as required tag entries.
    # Preserve its real entries while marking absent optional integration IDs optional.
    import zipfile
    lock=json.loads(Path('pack/mods.lock.json').read_text());cookery=next(m for m in lock['mods'] if m['project']=='kaleidoscope-cookery')
    with zipfile.ZipFile(Path('pack-runtime')/cookery['filename']) as jar:
        for resource in ['data/carryon/tags/block/block_blacklist.json','data/kaleidoscope_cookery/tags/item/cookery_mod_items.json']:
            tag=json.loads(jar.read(resource));tag['replace']=True
            tag['values']=[{'id':entry,'required':False} if isinstance(entry,str) and 'kalc/quark/' in entry else entry for entry in tag['values']]
            write(resource,tag)
    # Simple Planes references an item-only vanilla tag from a block tag. Supply its real block IDs.
    fireproof=[wood+'_'+part for wood in ['crimson','warped'] for part in ['stem','hyphae','planks','slab','pressure_plate','fence','trapdoor','fence_gate','stairs','button','door','sign','hanging_sign']]
    fireproof += ['stripped_'+wood+'_'+part for wood in ['crimson','warped'] for part in ['stem','hyphae']]
    write('data/simpleplanes/tags/block/fireproof_materials.json',{'replace':True,'values':['minecraft:'+name for name in fireproof]})
    print('Generated themed rewards, six larger authored landmarks and native survival milestones.')
    import generate_soaring_islands
    generate_soaring_islands.main()


if __name__=='__main__':main()
