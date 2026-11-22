"""Write the minimal reviewed pack overrides without copying any launcher state."""
from pathlib import Path
import json
import re


def main():
    root = Path('pack/overrides/config')
    root.mkdir(parents=True, exist_ok=True)
    base = json.loads(Path('pack/defaults/farskies.json').read_text())
    base.update(intervalSeconds=30, campaignEnabled=True,
                homeDistance=80, marketRadiusChunks=2, marketOffsetZ=24, spawnY=150,
                configVersion=5, truceMinutes=30, marketSafeMargin=12, eventMinutes=45, tierThresholds=[0,90,240,480,900,1440])
    for pool in base['pools']:
        for drop in pool:
            if drop['item'] == 'minecraft:trial_key': drop['item'] = 'minecraft:wither_skeleton_skull'
            if drop['item'] == 'minecraft:ominous_bottle': drop['item'] = 'minecraft:enchanted_golden_apple'
            drop['weight'] = 1 if drop['item'] in ['minecraft:netherite_scrap','minecraft:totem_of_undying','minecraft:heart_of_the_sea','minecraft:wither_skeleton_skull','minecraft:enchanted_golden_apple'] else 8
    early = {**{'minecraft:'+name:1 for name in ['iron_pickaxe','iron_sword','shield','bow','chainmail_chestplate']},
             'minecraft:arrow':12, 'minecraft:iron_ingot':4,
             **{'create:'+name:(4 if name=='shaft' else 1) for name in ['andesite_alloy','shaft','cogwheel','large_cogwheel','water_wheel','hand_crank','gearbox','mechanical_press','mechanical_mixer','basin']}}
    existing = {d['item']:d for d in base['pools'][0]}
    for name,count in early.items():
        if name in existing: existing[name].update(count=count, weight=1)
        else: base['pools'][0].append({'item':name,'count':count,'weight':1})
    for tier,item in [(0,'createsifter:string_mesh'),(5,'minecraft:netherite_upgrade_smithing_template')]:
        if not any(d['item']==item for d in base['pools'][tier]):base['pools'][tier].append({'item':item,'count':1,'weight':2 if tier==0 else 1})
    for pool in base['pools']:
        for drop in pool:drop['count']=1
    (root / 'farskies.json').write_text(json.dumps(base, indent=2) + '\n')
    qa = dict(base, campaignEnabled=False)
    for folder in ['run-qa/config', 'run-gametest/config']:
        Path(folder).mkdir(parents=True, exist_ok=True)
    Path('run-gametest/config/farskies.json').write_text(json.dumps(qa, indent=2) + '\n')
    Path('run-qa/config/farskies.json').write_text(json.dumps(base, indent=2) + '\n')
    (root / 'farskies-luck.json').write_bytes(Path('src/main/resources/farskies-luck.json').read_bytes())
    lock = json.loads(Path('pack/mods.lock.json').read_text())
    shader = lock['shaders'][0]['filename']
    # One world-preset owner. Ex Deorum supplies resource processing, not an extra starter island.
    for folder in [root,Path('run-qa/config'),Path('run-gametest/config')]:
        (folder/'exdeorum-common.toml').write_text('[common]\nset_void_world_as_default = false\nvoid_nether_generation = false\nvoid_end_generation = false\n')
        (folder/'exdeorum-server.toml').write_text('[server]\nstarting_torch = false\nstarting_watering_can = false\n')
    # Iris is client only; shaders can be toggled from Video Settings or with its keybind.
    (root / 'iris.properties').write_text(f'enableShaders=true\nshaderPack={shader}\n')
    cc = root / 'chancecubes'; cc.mkdir(exist_ok=True)
    settings = {'ChanceRangeMin': 10, 'ChanceRangeMax': 10, 'RewardsEqualChance': False,
                'DisableGiantCC': False, 'EnableDefaultRewards': True, 'GenerateAsOre': False,
                'GenerateOnSurface': False, 'ChestLoot': False, 'UserSpecificRewards': False,
                'GloballyDisabledRewards': False, 'HolidayRewards': False}
    (cc / 'chancecubes-server.toml').write_text('["General Settings"]\n' + '\n'.join(f'{k} = {json.dumps(v)}' for k,v in settings.items()) + '\n')
    # Quiet, readable presentation. These are defaults only; the local updater
    # never replaces an existing preference file.
    for source in Path('pack/defaults/polish').rglob('*'):
        if source.is_file():
            path=root/source.relative_to('pack/defaults/polish')
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(source.read_bytes())
    # The alpha's server integration tests use these exact worldgen and lucky-block settings.
    for folder in ['run-qa/config/chancecubes', 'run-gametest/config/chancecubes']:
        Path(folder).mkdir(parents=True, exist_ok=True)
        (Path(folder) / 'chancecubes-server.toml').write_bytes((cc / 'chancecubes-server.toml').read_bytes())


if __name__ == '__main__': main()
