"""Produce the Modrinth pack template, the release JAR and the no-Python server archive.

Third-party JARs remain official CDN downloads. No mod assets are copied into our JAR.

The core mod (farskies) is a normal `files` entry in the pack index. Its Modrinth CDN URL
only exists after the mod version is uploaded, so this script writes
`dist/Random-Item-Skyblock-<version>.mrpack-template` whose core entry carries the local
JAR's real hashes and size and a placeholder URL. `publish/upload.py` uploads the mod,
then writes the final `.mrpack` with the uploaded file's URL, hashes and size.
"""
from pathlib import Path
import hashlib
import json
import shutil
import ssl
import urllib.request
import zipfile

CORE_PLACEHOLDER = 'https://cdn.modrinth.com/data/UNRESOLVED/versions/UNRESOLVED/'
REQUIRED_ON_SERVER = {'create', 'chance-cubes', 'pick-up-notifier', 'puzzles-lib', 'skyblock-biome-islands',
                      'robs-floating-islands', 'open-parties-and-claims'}


def tls_context():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        return ssl.create_default_context()


def file_hashes(path):
    data = Path(path).read_bytes()
    return {'sha1': hashlib.sha1(data).hexdigest(), 'sha512': hashlib.sha512(data).hexdigest()}, len(data)


def write_text(z, name, text, mode=0o644):
    info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = (0o100000 | mode) << 16
    z.writestr(info, text)


def main():
    dist = Path('dist'); dist.mkdir(exist_ok=True)
    version = next(l.split('=', 1)[1] for l in Path('gradle.properties').read_text().splitlines() if l.startswith('mod_version='))
    jar = Path(f'build/libs/random-item-skyblock-{version}.jar')
    if not jar.is_file(): raise RuntimeError('Build the mod first')
    lock = json.loads(Path('pack/mods.lock.json').read_text())
    installer_url = f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{lock["neoforge"]}/neoforge-{lock["neoforge"]}-installer.jar'
    with urllib.request.urlopen(installer_url + '.sha512', context=tls_context(), timeout=30) as response:
        installer_hash = response.read().decode().strip().split()[0]
    if len(installer_hash) != 128: raise RuntimeError('Invalid installer hash')
    lock['installer'] = {'url': installer_url, 'sha512': installer_hash}

    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': version,
             'name': 'Random Item Skyblock',
             'summary': 'Two skyblock teams, a random item every 30 seconds, Create, useful islands and a neutral market.',
             'files': [], 'dependencies': {'minecraft': lock['minecraft'], 'neoforge': lock['neoforge']}}
    core_hashes, core_size = file_hashes(jar)
    index['files'].append({'path': 'mods/' + jar.name, 'hashes': core_hashes,
                           'downloads': [CORE_PLACEHOLDER + jar.name], 'fileSize': core_size,
                           'env': {'client': 'required', 'server': 'required'}})
    for mod in lock['mods']:
        index['files'].append({'path': 'mods/' + mod['filename'], 'hashes': mod['hashes'],
                               'downloads': [mod['url']], 'fileSize': mod['size'],
                               # Server-only content is also required by an integrated client server.
                               'env': {'client': 'required',
                                       'server': 'required' if mod['project'] in REQUIRED_ON_SERVER else mod['server']}})
    for shader in lock.get('shaders', []):
        index['files'].append({'path': 'shaderpacks/' + shader['filename'], 'hashes': shader['hashes'],
                               'downloads': [shader['url']], 'fileSize': shader['size'],
                               'env': {'client': 'required', 'server': 'unsupported'}})
    credits = '\n'.join(f"- {mod['project']} {mod['version']}: {mod['source']} ({mod['license']})" for mod in lock['mods'] + lock.get('shaders', [])) + '\n'
    (dist / f'MOD-CREDITS-{version}.md').write_text(credits, encoding='utf-8')

    template = dist / f'Random-Item-Skyblock-{version}.mrpack-template'
    with zipfile.ZipFile(template, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('modrinth.index.json', json.dumps(index, indent=2))
        for file in sorted(Path('pack/overrides').rglob('*')):
            if file.is_file(): z.write(file, 'overrides/' + file.relative_to('pack/overrides').as_posix())
        z.write('README.md', 'overrides/RANDOM-ITEM-SKYBLOCK-README.md')
        for name in ['docs/verification.md', 'docs/design/final-design.md', 'LICENSE']:
            z.write(name, 'overrides/' + name)
        z.writestr('overrides/MOD-CREDITS.md', credits)

    server_files = ['# kind\tfilename\turl\tsha512 - read by start.ps1 and start.sh']
    for mod in lock['mods']:
        if mod['server'] == 'unsupported': continue
        if Path(mod['filename']).name != mod['filename']: raise RuntimeError('Invalid locked filename')
        server_files.append('\t'.join(['mod', mod['filename'], mod['url'], mod['hashes']['sha512']]))
    server_files.append('\t'.join(['installer', f'neoforge-{lock["neoforge"]}-installer.jar', installer_url, installer_hash]))
    with zipfile.ZipFile(dist / f'Random-Item-Skyblock-{version}-server.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(jar, 'mods/' + jar.name)
        write_text(z, 'start.sh', Path('pack/server/start.sh').read_bytes().replace(b'\r\n', b'\n'), 0o755)
        for name in ['START-SERVER.cmd', 'start.ps1', 'SERVER-README.txt']:
            write_text(z, name, Path('pack/server', name).read_bytes().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n'))
        z.write('pack/server/server.properties', 'server.properties')
        for file in sorted(Path('pack/overrides').rglob('*')):
            if file.is_file() and file.name not in {'iris.properties', 'emi.css'}: z.write(file, file.relative_to('pack/overrides').as_posix())
        write_text(z, 'server-files.tsv', '\n'.join(server_files) + '\n')
        z.writestr('mods.lock.json', json.dumps(lock, indent=2))
        z.write('README.md', 'README.md')
        for name in ['docs/verification.md', 'docs/design/final-design.md', 'LICENSE']:
            z.write(name, name)
        z.writestr('MOD-CREDITS.md', credits)
    shutil.copy2(jar, dist / jar.name)
    manifest = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(dist.iterdir())
                if p.suffix in ['.jar', '.zip', '.mrpack', '.mrpack-template']}
    (dist / 'SHA256SUMS.json').write_text(json.dumps(manifest, indent=2) + '\n')
    for name in manifest:
        if version in name: print(name, (dist / name).stat().st_size)


if __name__ == '__main__': main()
