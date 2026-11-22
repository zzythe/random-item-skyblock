"""Backed-up update of this PC's Random Item Skyblock profile. Preserves builds and other profiles."""
from pathlib import Path
from datetime import datetime
import argparse
import hashlib
import json
import os
import shutil
import sqlite3
import time
import zipfile
import subprocess
import msvcrt
import uuid
import nbtlib

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha512(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--preferences-from', type=Path)
    args = parser.parse_args()
    profile = args.profile.resolve()
    profiles = (Path(os.environ['APPDATA']) / 'ModrinthApp/profiles').resolve()
    if profile.parent != profiles or not (profile / 'mods').is_dir():
        raise RuntimeError("Target must be an existing Modrinth profile in this PC's profile directory")
    needle=str(profile).replace("'","''")
    running=subprocess.run(['powershell','-NoProfile','-Command',"Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -and $_.CommandLine.Contains('"+needle+"') } | Select-Object -ExpandProperty ProcessId"],capture_output=True,text=True,check=True)
    if running.stdout.strip():raise RuntimeError('Save and quit Minecraft in this profile before updating it')
    if args.preferences_from and args.preferences_from.resolve() == profile:
        raise RuntimeError('Preference source must differ from the target')
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    lock = json.loads((ROOT / 'pack/mods.lock.json').read_text())
    replacements = {}
    for mod in lock['mods']:
        source = ROOT / 'pack-runtime' / mod['filename']
        if digest(source) != mod['hashes']['sha512']:
            raise RuntimeError('Hash mismatch: ' + source.name)
        replacements[Path('mods') / source.name] = source
    jar = ROOT / f'dist/random-item-skyblock-{version}.jar'
    if not jar.is_file():
        raise RuntimeError('Build and package the release first')
    replacements[Path('mods') / jar.name] = jar
    replacements[Path('MOD-CREDITS.md')] = ROOT / f'dist/MOD-CREDITS-{version}.md'
    replacements[Path('LICENSE')] = ROOT / 'LICENSE'
    for shader in lock.get('shaders', []):
        source = ROOT / 'pack-runtime' / shader['filename']
        if digest(source) != shader['hashes']['sha512']:
            raise RuntimeError('Hash mismatch: ' + source.name)
        replacements[Path('shaderpacks') / source.name] = source
    for source in (ROOT / 'pack/overrides').rglob('*'):
        if source.is_file():
            relative=source.relative_to(ROOT / 'pack/overrides')
            # Pack configs are defaults for new installs. Existing preferences and mod
            # settings belong to this instance; schema upgrades run in the mod itself.
            if relative.parts[0]=='config' and (profile/relative).is_file():continue
            replacements[relative] = source
    for name in ['README.md', 'docs/verification.md', 'docs/design/final-design.md']:
        replacements[Path('RANDOM-ITEM-SKYBLOCK-README.md') if name == 'README.md' else Path(name)] = ROOT / name
    if args.preferences_from:
        source = args.preferences_from.resolve() / 'options.txt'
        if not source.is_file():
            raise RuntimeError('Preference source has no options.txt')
        replacements[Path('options.txt')] = source
    # Recognise only files from this project's previous distributions, never arbitrary user mods.
    old_names = set()
    for archive in list((ROOT / 'dist').glob('Far-Skies-*.mrpack')) + list((ROOT / 'dist').glob('Random-Item-Skyblock-*.mrpack')):
        with zipfile.ZipFile(archive) as z:
            index = json.loads(z.read('modrinth.index.json'))
            old_names.update(Path(f['path']).name for f in index['files'] if f['path'].startswith('mods/'))
    old_names.update(p.name for p in (profile / 'mods').glob('far-skies-*.jar'))
    old_names.update(p.name for p in (profile / 'mods').glob('random-item-skyblock-*.jar'))
    wanted = {p.name for p in replacements if p.parts[0] == 'mods'}
    obsolete = [p for p in (profile / 'mods').iterdir() if p.is_file() and p.name in old_names - wanted]
    obsolete.extend(p for p in [profile / 'config/sky_archipelago-common.toml',profile / 'FAR-SKIES-README.md'] if p.is_file())
    backup = profile / '.farskies-backups' / datetime.now().strftime('%Y%m%d-%H%M%S')
    backup.mkdir(parents=True, exist_ok=False)
    locks=[];world_updates=[]
    for world in (profile/'saves').iterdir():
        if not world.is_dir() or not (world/'level.dat').is_file():continue
        level=nbtlib.load(world/'level.dat');generator=level['Data'].get('WorldGenSettings',{}).get('dimensions',{}).get('minecraft:overworld',{}).get('generator',{})
        if generator.get('type')!='minecraft:flat' or generator.get('settings',{}).get('biome')!='farskies:open_sky':continue
        stream=(world/'session.lock').open('r+b');msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1);locks.append(stream)
        archive=backup/('save-'+world.name+'.zip')
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as saved:
            for path in world.rglob('*'):
                if path.is_file() and path.name!='session.lock':saved.write(path,path.relative_to(world).as_posix())
        value=generator['settings'].get('structure_overrides',[])
        overrides=[str(value)] if isinstance(value,nbtlib.String) else list(map(str,value))
        added=[name for name in ['farskies:landmarks','farskies:soaring'] if name not in overrides]
        if added:
            overrides.extend(added);generator['settings']['structure_overrides']=nbtlib.List[nbtlib.String](overrides)
            temporary=world/'level.dat.farskies-new';level.save(temporary,gzipped=True);os.replace(temporary,world/'level.dat')
            world_updates.append(world.name+': enable '+', '.join(added)+' in newly explored chunks')
    changes = []
    for target in obsolete:
        rel = target.relative_to(profile)
        saved = backup / rel
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(target), str(saved))
        changes.append('removed ' + str(rel))
    for rel, source in replacements.items():
        target = profile / rel
        if target.is_file() and digest(target) == digest(source):
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            saved = backup / rel
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, saved)
        # Replace the directory entry, preserving any other profile/cache hardlinks.
        temporary = target.with_name(target.name + '.farskies-new')
        shutil.copy2(source, temporary)
        os.replace(temporary, target)
        changes.append('installed ' + str(rel))
    # Keep the existing instance path and identity; only update its displayed project name.
    database=profiles.parent / 'app.db'
    previous_name=None
    if database.is_file():
        with sqlite3.connect(database, timeout=10) as connection:
            connection.execute('PRAGMA foreign_keys=ON')
            row=connection.execute('SELECT id,name FROM instances WHERE path=?',(profile.name,)).fetchone()
            if row and row[1]!='Random Item Skyblock':
                previous_name={'id':row[0],'name':row[1]}
                (backup/'instance-name.json').write_text(json.dumps(previous_name,indent=2)+'\n')
                connection.execute('UPDATE instances SET name=?,modified=? WHERE id=?',('Random Item Skyblock',int(time.time()),row[0]))
            if row:
                iid=row[0];sid=connection.execute('SELECT applied_content_set_id FROM instances WHERE id=?',(iid,)).fetchone()[0];now=int(time.time())
                for mod in lock['mods']+[{'filename':jar.name,'project_id':None,'version_id':None,'server':'required'}]:
                    file=profile/'mods'/mod['filename'];relative='mods/'+file.name
                    record=connection.execute('SELECT id FROM instance_files WHERE instance_id=? AND relative_path=?',(iid,relative)).fetchone()
                    if record:
                        fid=record[0];connection.execute('UPDATE instance_files SET missing=0,enabled=1,sha1=?,size=?,modified_at=? WHERE id=?',(hashlib.sha1(file.read_bytes()).hexdigest(),file.stat().st_size,now,fid))
                    else:
                        fid='instance-file:'+str(uuid.uuid4());connection.execute('INSERT INTO instance_files(id,instance_id,relative_path,file_name,enabled,sha1,size,missing,added_at,modified_at) VALUES(?,?,?,?,?,?,?,?,?,?)',(fid,iid,relative,file.name,1,hashlib.sha1(file.read_bytes()).hexdigest(),file.stat().st_size,0,now,now))
                        connection.execute('INSERT INTO instance_content_entries(id,instance_id,content_set_id,file_id,project_type,project_id,version_id,source_kind,server_requirement,client_requirement,enabled,added_at,modified_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',('content-entry:'+str(uuid.uuid4()),iid,sid,fid,'mod',mod['project_id'],mod['version_id'],'imported_modpack',mod.get('server','required'),'required',1,now,now))
                for record in connection.execute('SELECT id,file_name FROM instance_files WHERE instance_id=?',(iid,)).fetchall():
                    if record[1] in old_names-wanted or record[1].startswith(('far-skies-','random-item-skyblock-')) and record[1]!=jar.name:
                        connection.execute('DELETE FROM instance_content_entries WHERE instance_id=? AND file_id=?',(iid,record[0]));connection.execute('DELETE FROM instance_files WHERE id=?',(record[0],))
                connection.execute('UPDATE instance_links SET imported_version_number=?,imported_filename=? WHERE instance_id=? AND link_kind=?',(version,f'Random-Item-Skyblock-{version}.mrpack',iid,'imported_modpack'))
    report = {'version': version, 'profile': str(profile), 'backup': str(backup),
              'preferences_from': str(args.preferences_from) if args.preferences_from else None,
              'changes': changes, 'saves_modified': bool(world_updates),'world_updates':world_updates, 'previous_instance_name': previous_name}
    (backup / 'update.json').write_text(json.dumps(report, indent=2) + '\n')
    (ROOT / '.tools/local-profile-update.json').write_text(json.dumps(report, indent=2) + '\n')
    for stream in locks:stream.close()
    print(f'Updated {profile.name} to {version}. {len(changes)} file changes. Backup: {backup}')


if __name__ == '__main__':
    main()
