"""Resolve required compatible dependencies and world-content mods, pinning every hash."""
from __future__ import annotations
import json
from pathlib import Path
from urllib.parse import urlencode
from resolve_pack import api


def entry(project, version):
    file = next((f for f in version['files'] if f['primary']), version['files'][0])
    return {'project': project['slug'], 'project_id': project['id'],
            'version_id': version['id'], 'version': version['version_number'],
            'minecraft': version['game_versions'], 'loaders': version['loaders'],
            'filename': file['filename'], 'url': file['url'], 'size': file['size'],
            'hashes': file['hashes'], 'dependencies': version['dependencies'],
            'client': project['client_side'], 'server': project['server_side'],
            'license': project['license']['id'],
            'source': 'https://modrinth.com/' + project.get('project_type', 'mod') + '/' + project['slug']}


def main():
    path = Path('pack/mods.lock.json')
    lock = json.loads(path.read_text())
    removed = {'sky-archipelago'}
    mods = {m['project_id']: m for m in lock['mods'] if m['project'] not in removed}
    query = urlencode({'game_versions': '["1.21.1"]', 'loaders': '["neoforge"]'})

    def add(slug, version_id=None):
        project = api('project/' + slug)
        if project['id'] in mods and (version_id is None or mods[project['id']]['version_id'] == version_id):
            return
        if version_id:
            version = api('version/' + version_id)
        else:
            versions = api('project/' + project['id'] + '/version?' + query)
            if not versions:
                raise RuntimeError('No compatible version of ' + slug)
            version = next((v for v in versions if v['version_type'] == 'release'), versions[0])
        if '1.21.1' not in version['game_versions'] or 'neoforge' not in version['loaders']:
            raise RuntimeError('Incompatible dependency ' + slug)
        mod = entry(project, version)
        mods[project['id']] = mod
        print(mod['project'], mod['version'])

    add('skyblock-biome-islands', 'DUvhFoWC')
    add('robs-floating-islands', 'qchytHxX')
    add('open-parties-and-claims')
    add('chance-cubes', '1KizaIYB')
    add('pick-up-notifier', '5NZounJc')
    # EMI owns the interface; JEI supplies Create/Sifter recipe plugins to EMI.
    add('emi', '5sIPA1To')
    add('iris', 't3ruzodq')
    add('sodium', 'Pb3OXVqC')
    add('lithium', 'DDUrRVCA')
    add('entityculling', 'eieYbWYb')
    add('immediatelyfast', 'EJzmEPZV')
    add('dynamic-fps', 'T238FZpQ')
    add('zume', 'Rh2ImJ86')
    add('kaleidoscope-cookery', 'g8w0b2ly')
    add('embers-floating-islands', 'NX2vCZkz')
    # Large vanilla-block sky islands for exploration (placed only through farskies:soaring).
    add('mss-moogs-soaring-structures', 'O20bIWkj')
    add('moogs-structure-lib', 'wcg4mE4e')
    # Aircraft for crossing the void between distant islands and bases.
    add('simple-planes', 'tGW6Zlf9')
    add('mouse-tweaks', '9I21YYxf')
    add('3dskinlayers', 'GJTnQGfe')
    add('ambientsounds', 'WGalCQAo')
    add('creativecore', 'vRMtFt35')
    add('seamless-sleep', 'Lw02ZJke')
    add('yacl', '7TVdVtxF')
    add('subtle-effects', 'ReUDmzrA')
    add('fzzy-config', 'jRuOkBAG')
    while True:
        missing = [d for m in mods.values() for d in m['dependencies']
                   if d['dependency_type'] == 'required' and d['project_id'] not in mods]
        if not missing:
            break
        for dep in missing:
            if not dep['project_id']:
                raise RuntimeError('Unresolved embedded dependency: ' + str(dep))
            add(dep['project_id'], dep['version_id'])
    lock['mods'] = list(mods.values())
    project = api('project/complementary-reimagined')
    version = api('version/Bqen1mJX')
    if '1.21.1' not in version['game_versions'] or 'iris' not in version['loaders']:
        raise RuntimeError('Incompatible shader pack')
    shader = entry(project, version)
    shader['source'] = 'https://modrinth.com/shader/' + project['slug']
    lock['shaders'] = [shader]
    path.write_text(json.dumps(lock, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
