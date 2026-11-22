"""Lock exact installed mod versions through the public Modrinth hash API.

Reads only selected mod JARs, never account or launcher databases.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

USER_AGENT = 'FarSkies/0.1 (private modpack development)'


def api(path: str, body: dict | None = None):
    raw = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request('https://api.modrinth.com/v2/' + path, data=raw,
                                 headers={'User-Agent': USER_AGENT, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('profile', type=Path)
    parser.add_argument('--out', type=Path, default=Path('pack/mods.lock.json'))
    args = parser.parse_args()
    # A lean candidate set. World generation and faction membership have one owner.
    patterns = ['create-1.21.1-*', 'createsifter-*', 'mechanicals-*', 'jei-*',
                'Jade-*', 'ferritecore-*', 'modernfix-*', 'appleskin-*']
    jars = []
    for pattern in patterns:
        matches = list((args.profile / 'mods').glob(pattern + '.jar'))
        if len(matches) != 1:
            raise RuntimeError(f'Expected one {pattern}, got {len(matches)}')
        jar = matches[0]
        jars.append((jar, hashlib.sha512(jar.read_bytes()).hexdigest()))
    found = api('version_files', {'hashes': [h for _, h in jars], 'algorithm': 'sha512'})
    entries = []
    for jar, digest in jars:
        version = found.get(digest)
        if not version:
            raise RuntimeError(f'{jar.name} is not indexed by Modrinth; do not silently redistribute it')
        file = next(f for f in version['files'] if f['hashes']['sha512'] == digest)
        project = api('project/' + version['project_id'])
        entries.append({'project': project['slug'], 'project_id': project['id'],
                        'version_id': version['id'], 'version': version['version_number'],
                        'minecraft': version['game_versions'], 'loaders': version['loaders'],
                        'filename': file['filename'], 'url': file['url'], 'size': file['size'],
                        'hashes': file['hashes'], 'dependencies': version['dependencies'],
                        'client': project['client_side'], 'server': project['server_side'],
                        'license': project['license']['id'],
                        'source': 'https://modrinth.com/mod/' + project['slug']})
        print(project['slug'], version['version_number'])
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps({'minecraft': '1.21.1', 'neoforge': '21.1.265',
                                    'mods': entries}, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
