"""Check the produced files without starting a public server or accepting the EULA."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import zipfile


def main():
    dist = Path('dist')
    version = next(l.split('=', 1)[1] for l in Path('gradle.properties').read_text().splitlines() if l.startswith('mod_version='))
    # The template becomes the published .mrpack once publish/upload.py fills in the core mod's CDN URL.
    with zipfile.ZipFile(dist / f'Random-Item-Skyblock-{version}.mrpack-template') as z:
        assert z.testzip() is None
        index = json.loads(z.read('modrinth.index.json'))
        assert index['dependencies'] == {'minecraft': '1.21.1', 'neoforge': '21.1.265'}
        config = json.loads(z.read('overrides/config/farskies.json'))
        assert config['intervalSeconds'] == 30 and config['campaignEnabled'] is True
        assert config['truceMinutes'] == 30
        assert 'EnableDefaultRewards = true' in z.read('overrides/config/chancecubes/chancecubes-server.toml').decode()
        assert 'sleepCameraTiltDegrees = 0.0' in z.read('overrides/config/seamless_sleep.toml').decode()
        assert 'particlesDisplayType = "OFF"' in z.read('overrides/config/subtle_effects/items.toml').decode()
        assert len(config['pools']) == 6
        assert all(drop['count']==1 for pool in config['pools'] for drop in pool)
        assert config['factionNames'] == ['Red','Blue'] and config['tierNames'] == [f'Tier {i}' for i in range(1,7)]
        assert config['tierThresholds'] == [0,90,240,480,900,1440]
        assert config['homeDistance'] == 80 and config['marketOffsetZ'] == 24
        assert len(json.loads(z.read('overrides/config/farskies-luck.json'))) == 32
        assert any('FarmersDelight' in f['path'] for f in index['files'])
        lock = json.loads(Path('pack/mods.lock.json').read_text())
        assert len(index['files']) == len(lock['mods']) + len(lock.get('shaders', [])) + 1
        jar_bytes = (dist / f'random-item-skyblock-{version}.jar').read_bytes()
        core = [f for f in index['files'] if f['path'] == f'mods/random-item-skyblock-{version}.jar']
        assert len(core) == 1 and core[0]['env'] == {'client': 'required', 'server': 'required'}
        assert core[0]['hashes'] == {'sha1': hashlib.sha1(jar_bytes).hexdigest(), 'sha512': hashlib.sha512(jar_bytes).hexdigest()}
        assert core[0]['fileSize'] == len(jar_bytes)
        assert not any(n.startswith('overrides/mods/') for n in z.namelist()), 'The core mod must not be bundled in overrides'
        for file in index['files']:
            assert file['path'].startswith(('mods/', 'shaderpacks/')) and '..' not in Path(file['path']).parts
            assert len(file['hashes']['sha512']) == 128
            assert file['downloads'][0].startswith('https://cdn.modrinth.com/')
        assert not {'sky-archipelago'} & {m['project'] for m in lock['mods']}
        assert {'mouse-tweaks','3dskinlayers','ambientsounds','creativecore','seamless-sleep','yacl','subtle-effects','fzzy-config','kotlin-for-forge'} <= {m['project'] for m in lock['mods']}
        projects={m['project_id'] for m in lock['mods']}
        assert all(d['project_id'] in projects for m in lock['mods'] for d in m['dependencies'] if d['dependency_type']=='required')
        assert not any(n.startswith(('overrides/saves/','overrides/logs/')) or n=='overrides/options.txt' for n in z.namelist())
        for mod in lock['mods']:
            f = next(f for f in index['files'] if f['path'] == 'mods/' + mod['filename'])
            assert f['env']['client'] == 'required', 'Integrated servers need every core mod'
            if mod['server'] == 'unsupported': assert f['env']['server'] == 'unsupported'
        print('Modrinth manifest, settings, outcomes, and locked downloads verified:', len(index['files']), 'mods')
    with zipfile.ZipFile(dist / f'random-item-skyblock-{version}.jar') as z:
        names = z.namelist()
        assert not any(n.endswith('.nbt') or n.startswith('dev/farskies/qa/') or 'GameTests' in n for n in names)
        assert 'farskies.mixins.json' in names
        assert 'LICENSE' in names and 'data/farskies/worldgen/world_preset/far_skies.json' in names
        assert not any('ProgressDisplay' in n or 'FieldLedger' in n or 'field_ledger' in n or 'TeamScreen' in n for n in names)
        preset=json.loads(z.read('data/farskies/worldgen/world_preset/far_skies.json'))
        assert preset['dimensions']['minecraft:overworld']['generator']['type']=='minecraft:flat'
        assert preset['dimensions']['minecraft:overworld']['generator']['settings']['structure_overrides']==['farskies:islands','farskies:landmarks','farskies:soaring']
        assert len(json.loads(z.read('data/farskies/worldgen/structure_set/islands.json'))['structures'])==26
        assert len(json.loads(z.read('data/farskies/worldgen/structure_set/landmarks.json'))['structures'])==6
        assert len(json.loads(z.read('data/farskies/worldgen/structure_set/soaring.json'))['structures'])==34
        assert {'mss-moogs-soaring-structures','moogs-structure-lib','simple-planes','ex-deorum'} <= {m['project'] for m in lock['mods']}
        assert 'dev/farskies/PlaytestGameTests.class' not in names
        assert 'dev/farskies/client/SkyblockEmiPlugin.class' in names
        assert 'data/simpleplanes/tags/block/fireproof_materials.json' in names
        assert 'dev/farskies/world/IslandStructure.class' in names and 'dev/farskies/ItemDrops.class' in names
        print('Release JAR contains the void preset and no obsolete UI, QA classes or structure assets')
    target = Path(f'.tools/distribution-smoke-{version}')
    if target.exists():
        import shutil; shutil.rmtree(target)
    target.mkdir(parents=True)
    with zipfile.ZipFile(dist / f'Random-Item-Skyblock-{version}-server.zip') as z:
        assert z.testzip() is None
        names = z.namelist()
        assert all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
        assert {'START-SERVER.cmd', 'start.ps1', 'start.sh', 'server-files.tsv', f'mods/random-item-skyblock-{version}.jar'} <= set(names)
        assert 'server.py' not in names and 'eula.txt' not in names
        assert b'\r\n' not in z.read('start.sh') and (z.getinfo('start.sh').external_attr >> 16) & 0o111
        rows = [r.split('\t') for r in z.read('server-files.tsv').decode().splitlines() if r and not r.startswith('#')]
        assert sum(r[0] == 'installer' for r in rows) == 1
        assert len([r for r in rows if r[0] == 'mod']) == len([m for m in lock['mods'] if m['server'] != 'unsupported'])
        z.extractall(target)
    # Declining the EULA prompt must stop before any download and must not write eula.txt.
    result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(target / 'start.ps1')],
                            input='no\n', capture_output=True, text=True, timeout=120)
    assert result.returncode == 2 and 'EULA was not accepted' in result.stdout, result.stdout + result.stderr
    assert not (target / 'eula.txt').exists() and not (target / 'runtime').exists()
    print('Server archive layout, LF start.sh, download manifest and EULA refusal passed')
    print('Full first boot: python publish/tools/boot_test.py dist/Random-Item-Skyblock-<version>-server.zip <empty folder>')


if __name__ == '__main__': main()
