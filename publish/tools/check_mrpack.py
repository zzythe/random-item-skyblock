"""Validate a .mrpack (or the .mrpack-template) fully: structure, then download every file and compare
its size, sha1 and sha512 with the index. Standard library only.

Usage: python publish/tools/check_mrpack.py <pack.mrpack|pack.mrpack-template> [--core-jar dist/random-item-skyblock-X.jar]
A template's placeholder core-mod URL is not downloaded; its hashes are checked against --core-jar instead.
"""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import sys
import urllib.request
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import upload  # noqa: E402  (validate_mrpack and USER_AGENT)


def fetch(entry):
    url = entry['downloads'][0]
    req = urllib.request.Request(url, headers={'User-Agent': upload.USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            data = resp.read()
    except OSError as error:
        return entry['path'], f'UNREACHABLE {url}: {error}'
    problems = []
    if len(data) != entry['fileSize']: problems.append(f'size {len(data)} != {entry["fileSize"]}')
    if hashlib.sha1(data).hexdigest() != entry['hashes']['sha1']: problems.append('sha1 mismatch')
    if hashlib.sha512(data).hexdigest() != entry['hashes']['sha512']: problems.append('sha512 mismatch')
    return entry['path'], ('OK' if not problems else 'BAD ' + ', '.join(problems))


def main():
    pack = Path(sys.argv[1])
    core_jar = Path(sys.argv[sys.argv.index('--core-jar') + 1]) if '--core-jar' in sys.argv else None
    for line in upload.validate_mrpack(pack): print(line)
    with zipfile.ZipFile(pack) as z:
        index = json.loads(z.read('modrinth.index.json'))
    remote, results = [], []
    for entry in index['files']:
        if '/UNRESOLVED/' in entry['downloads'][0]:
            if not core_jar: raise SystemExit('Template core entry needs --core-jar')
            data = core_jar.read_bytes()
            ok = (len(data) == entry['fileSize'] and hashlib.sha1(data).hexdigest() == entry['hashes']['sha1']
                  and hashlib.sha512(data).hexdigest() == entry['hashes']['sha512'])
            results.append((entry['path'], 'OK (local core JAR; CDN URL is set by upload.py)' if ok else 'BAD local core JAR differs'))
        else:
            remote.append(entry)
    with ThreadPoolExecutor(8) as pool:
        results += list(pool.map(fetch, remote))
    for path, status in sorted(results): print(f'{status:<10.60} {path}')
    bad = [r for r in results if not r[1].startswith('OK')]
    print(f'\n{len(results) - len(bad)}/{len(results)} files verified (downloaded and hashed: {len(remote)}).')
    return 1 if bad else 0


if __name__ == '__main__': raise SystemExit(main())
