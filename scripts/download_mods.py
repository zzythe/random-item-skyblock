"""Download locked official mod files to an isolated development directory."""
import hashlib
import json
from pathlib import Path
import urllib.request
import ssl


def download_all():
    root = Path('pack-runtime')
    root.mkdir(exist_ok=True)
    lock = json.loads(Path('pack/mods.lock.json').read_text())
    try:
        import certifi
        context = ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        context = ssl.create_default_context()
    for mod in lock['mods'] + lock.get('shaders', []):
        target = root / mod['filename']
        if target.name != mod['filename']:
            raise ValueError('Unsafe filename in lock')
        if not target.exists():
            req = urllib.request.Request(mod['url'], headers={'User-Agent': 'FarSkies/0.2'})
            with urllib.request.urlopen(req, context=context, timeout=120) as response:
                target.write_bytes(response.read())
        if hashlib.sha512(target.read_bytes()).hexdigest() != mod['hashes']['sha512']:
            raise RuntimeError('Hash mismatch: ' + target.name)
        print(target.name)
    return root


if __name__ == '__main__':
    download_all()
