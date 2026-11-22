"""Publish Random Item Skyblock to Modrinth (API v2). Python 3.10+ standard library only.

What it does, in order:
  1. Create the core mod project (if its slug does not exist yet) and upload the core mod version.
  2. Build the final .mrpack: the template's core-mod entry gets the uploaded file's real CDN URL,
     sha1, sha512 and size. Every other entry is copied unchanged. The result is validated.
  3. Create the modpack project (if absent) and upload the pack version.
  4. Upload icons and gallery images that are not there yet, fill the description's screenshot slots,
     and set Ko-fi donation links when --kofi is given.
  5. Leave both projects as drafts, unless --submit is given (then submit both for review).

Re-running is safe: existing projects, versions (matched by version number) and gallery images
(matched by title) are reused, never duplicated. Listing text and links are re-synced each run.

Usage:
  set MODRINTH_TOKEN=mrp_...           (PowerShell: $env:MODRINTH_TOKEN = 'mrp_...')
  python publish/upload.py --dry-run   prints every request, sends nothing, still builds the mrpack
  python publish/upload.py [--kofi https://ko-fi.com/NAME] [--server-zip-url URL] [--submit]
Token scopes needed: PROJECT_CREATE, PROJECT_WRITE, VERSION_CREATE, VERSION_READ, PROJECT_READ.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

HERE = Path(__file__).resolve().parent
USER_AGENT = 'zzythe/random-item-skyblock-publish/0.6.1 (modrinth.com/user/zzythe)'
ALLOWED_HOSTS = {'cdn.modrinth.com', 'github.com', 'raw.githubusercontent.com', 'gitlab.com'}
PLACEHOLDER_SERVER_URL = 'SERVER_ZIP_URL'


class Api:
    def __init__(self, base: str, token: str | None, dry: bool):
        self.base, self.token, self.dry, self.count = base.rstrip('/'), token, dry, 0

    def request(self, method: str, path: str, *, query: dict | None = None, json_body=None,
                raw: bytes | None = None, content_type: str | None = None, multipart: list | None = None,
                ok_missing: bool = False):
        url = self.base + path + ('?' + urllib.parse.urlencode(query) if query else '')
        headers = {'User-Agent': USER_AGENT}
        if self.token: headers['Authorization'] = self.token
        body, summary = None, None
        if json_body is not None:
            body = json.dumps(json_body).encode(); headers['Content-Type'] = 'application/json'
            summary = json.dumps(json_body, indent=2)
        elif raw is not None:
            body = raw; headers['Content-Type'] = content_type
            summary = f'<{len(raw)} bytes {content_type} sha1={hashlib.sha1(raw).hexdigest()}>'
        elif multipart is not None:
            body, ctype, summary = encode_multipart(multipart); headers['Content-Type'] = ctype
        self.count += 1
        if self.dry:
            shown = {k: ('<MODRINTH_TOKEN>' if k == 'Authorization' else v) for k, v in headers.items()}
            print(f'\n[dry-run #{self.count}] {method} {url}')
            print('  headers:', json.dumps(shown))
            if summary: print('  body:', summary.replace('\n', '\n  '))
            return None
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=300) as resp:
                data = resp.read()
                return json.loads(data) if data else {}
        except urllib.error.HTTPError as error:
            if ok_missing and error.code == 404: return None
            detail = error.read().decode(errors='replace')
            raise SystemExit(f'{method} {url} failed: HTTP {error.code}\n{detail}')


def encode_multipart(parts: list):
    """parts: (name, value) for text or (name, filename, bytes, content_type) for files."""
    boundary = '----ris' + uuid.uuid4().hex
    out, summary = bytearray(), []
    for part in parts:
        out += f'--{boundary}\r\n'.encode()
        if len(part) == 2:
            name, value = part
            out += f'Content-Disposition: form-data; name="{name}"\r\nContent-Type: application/json\r\n\r\n'.encode()
            out += value.encode() + b'\r\n'
            summary.append(f'part "{name}" (application/json):\n' + json.dumps(json.loads(value), indent=2))
        else:
            name, filename, data, ctype = part
            out += f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\nContent-Type: {ctype}\r\n\r\n'.encode()
            out += data + b'\r\n'
            summary.append(f'part "{name}" file {filename} ({ctype}, {len(data)} bytes, sha1={hashlib.sha1(data).hexdigest()})')
    out += f'--{boundary}--\r\n'.encode()
    return bytes(out), f'multipart/form-data; boundary={boundary}', '\n'.join(summary)


def fmt(value: str, version: str) -> str:
    return value.replace('{version}', version)


def hashes(data: bytes) -> dict:
    return {'sha1': hashlib.sha1(data).hexdigest(), 'sha512': hashlib.sha512(data).hexdigest()}


def project_data(cfg: dict, body: str) -> dict:
    data = {
        'slug': cfg['slug'], 'title': cfg['title'], 'description': cfg['summary'], 'body': body,
        'project_type': cfg['project_type'], 'categories': cfg['categories'],
        'additional_categories': cfg['additional_categories'],
        'client_side': cfg['client_side'], 'server_side': cfg['server_side'],
        'license_id': cfg['license_id'], 'license_url': cfg['license_url'],
        'source_url': cfg['source_url'], 'issues_url': cfg['issues_url'], 'wiki_url': None,
        'discord_url': cfg['discord_url'], 'donation_urls': [], 'initial_versions': [], 'is_draft': True,
        'requested_status': 'approved',
    }
    if not 3 <= len(data['description']) <= 255: raise SystemExit(f'{cfg["slug"]}: summary must be 3-255 characters')
    if len(data['categories']) > 3: raise SystemExit(f'{cfg["slug"]}: at most 3 primary categories')
    return data


def ensure_project(api: Api, cfg: dict, body: str, icon: bytes, sim_id: str) -> dict:
    """Return the project (dict with id, gallery, icon_url, status), creating it as a draft when absent."""
    existing = None if api.dry else api.request('GET', f'/project/{cfg["slug"]}', ok_missing=True)
    if existing:
        # Modrinth derives the type from uploaded versions, so a new draft reads as "project".
        if existing.get('project_type') not in (cfg['project_type'], 'project'):
            raise SystemExit(f'Slug {cfg["slug"]} is used by a {existing.get("project_type")} project you can see. Pick another slug in listing.json.')
        print(f'Project {cfg["slug"]} exists: {existing["id"]} ({existing["status"]})')
        edit = {k: v for k, v in project_data(cfg, body).items()
                if k in ('title', 'description', 'body', 'categories', 'additional_categories', 'license_url',
                         'source_url', 'issues_url', 'discord_url')}
        edit['license_id'] = cfg['license_id']
        api.request('PATCH', f'/project/{existing["id"]}', json_body=edit)
        if not existing.get('icon_url'):
            api.request('PATCH', f'/project/{existing["id"]}/icon', query={'ext': 'png'}, raw=icon, content_type='image/png')
        return existing
    if api.dry: print(f'\n(dry run assumes project {cfg["slug"]} does not exist yet)')
    created = api.request('POST', '/project', multipart=[('data', json.dumps(project_data(cfg, body))),
                                                         ('icon', 'icon.png', icon, 'image/png')])
    if api.dry: return {'id': sim_id, 'gallery': [], 'icon_url': None, 'status': 'draft', 'versions': []}
    print(f'Created draft project {cfg["slug"]}: {created["id"]}')
    return api.request('GET', f'/project/{created["id"]}')


def ensure_version(api: Api, project: dict, data: dict, filename: str, payload: bytes, ctype: str, sim_id: str) -> dict:
    if not api.dry:
        # A draft's versions need the "Read versions" scope; without it Modrinth answers 404 and
        # would happily accept a second copy of the same version, so refuse instead of guessing.
        versions = api.request('GET', f'/project/{project["id"]}/version', query={'include_changelog': 'false'}, ok_missing=True)
        if versions is None and project.get('versions'):
            raise SystemExit(f'{project["id"]} already has versions, but this token cannot read them. '
                             'Add the "Read versions" scope to the token and run again.')
        for v in versions or []:
            if v['version_number'] == data['version_number']:
                print(f'Version {data["version_number"]} of {project["id"]} exists: {v["id"]}')
                return v
    data = dict(data, project_id=project['id'], file_parts=['file'], primary_file='file')
    created = api.request('POST', '/version', multipart=[('data', json.dumps(data)), ('file', filename, payload, ctype)])
    if api.dry:
        h = hashes(payload)
        return {'id': sim_id, 'files': [{'url': f'https://cdn.modrinth.com/data/{project["id"]}/versions/{sim_id}/{filename}',
                                         'filename': filename, 'hashes': h, 'size': len(payload), 'primary': True}]}
    print(f'Uploaded version {data["version_number"]}: {created["id"]}')
    return created


def head_ok(url: str) -> bool:
    for method in ('HEAD', 'GET'):
        try:
            req = urllib.request.Request(url, method=method, headers={'User-Agent': USER_AGENT, 'Range': 'bytes=0-0'})
            with urllib.request.urlopen(req, timeout=60) as resp:
                if resp.status in (200, 206): return True
        except urllib.error.HTTPError as e:
            if e.code not in (403, 405): return False
        except OSError:
            return False
    return False


def build_mrpack(template: Path, output: Path, core_file: dict) -> bytes:
    """Copy the template, replacing the core mod entry with the uploaded file's URL, hashes and size."""
    with zipfile.ZipFile(template) as src:
        index = json.loads(src.read('modrinth.index.json'))
        core = [f for f in index['files'] if re.fullmatch(r'mods/random-item-skyblock-[^/]+\.jar', f['path'])]
        if len(core) != 1: raise SystemExit('The template must contain exactly one core mod entry')
        entry = core[0]
        if entry['path'] != 'mods/' + core_file['filename']: raise SystemExit('Uploaded core file name differs from the template entry')
        if entry['hashes']['sha512'] != core_file['hashes']['sha512'] or entry['fileSize'] != core_file['size']:
            raise SystemExit('The uploaded core mod differs from the JAR the template was built with. Rebuild the template.')
        entry['downloads'] = [core_file['url']]
        entry['hashes'] = {'sha1': core_file['hashes']['sha1'], 'sha512': core_file['hashes']['sha512']}
        entry['fileSize'] = core_file['size']
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as out:
            for info in src.infolist():
                data = json.dumps(index, indent=2).encode() if info.filename == 'modrinth.index.json' else src.read(info)
                fixed = zipfile.ZipInfo(info.filename, date_time=(2026, 1, 1, 0, 0, 0))
                fixed.compress_type = zipfile.ZIP_DEFLATED
                fixed.external_attr = 0o644 << 16
                out.writestr(fixed, data)
    validate_mrpack(output)
    return output.read_bytes()


def validate_mrpack(path: Path, check_urls: bool = False) -> list[str]:
    """Structural checks against the Modrinth .mrpack format. Returns a list of report lines."""
    report = []
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None: raise SystemExit('Corrupt zip entry')
        names = z.namelist()
        if 'modrinth.index.json' not in names: raise SystemExit('modrinth.index.json missing')
        index = json.loads(z.read('modrinth.index.json'))
        for key in ('formatVersion', 'game', 'versionId', 'name', 'files', 'dependencies'):
            if key not in index: raise SystemExit(f'index missing {key}')
        if index['formatVersion'] != 1 or index['game'] != 'minecraft': raise SystemExit('Unsupported index format')
        if set(index['dependencies']) - {'minecraft', 'forge', 'neoforge', 'fabric-loader', 'quilt-loader'}:
            raise SystemExit('Unknown dependency id')
        seen = set()
        for f in index['files']:
            p = f['path']
            if p in seen or p.startswith('/') or '..' in p.split('/') or '\\' in p: raise SystemExit(f'Bad path {p}')
            seen.add(p)
            if not re.fullmatch(r'[0-9a-f]{40}', f['hashes'].get('sha1', '')) or not re.fullmatch(r'[0-9a-f]{128}', f['hashes'].get('sha512', '')):
                raise SystemExit(f'Bad hashes for {p}')
            if not f['downloads']: raise SystemExit(f'No downloads for {p}')
            for url in f['downloads']:
                u = urllib.parse.urlparse(url)
                if u.scheme != 'https' or u.hostname not in ALLOWED_HOSTS: raise SystemExit(f'Download host not allowed: {url}')
            if not isinstance(f.get('fileSize'), int) or f['fileSize'] <= 0: raise SystemExit(f'Bad fileSize for {p}')
            env = f.get('env', {})
            if set(env.values()) - {'required', 'optional', 'unsupported'}: raise SystemExit(f'Bad env for {p}')
        for n in names:
            if n != 'modrinth.index.json' and not n.startswith(('overrides/', 'client-overrides/', 'server-overrides/')):
                raise SystemExit(f'Unexpected entry {n}')
            if '..' in n.split('/') or n.startswith('/'): raise SystemExit(f'Bad entry {n}')
        report.append(f'{path.name}: format 1, {len(index["files"])} files, deps {index["dependencies"]}, '
                      f'{sum(1 for n in names if n.startswith("overrides/"))} override files')
        if check_urls:
            bad = [f['downloads'][0] for f in index['files'] if not head_ok(f['downloads'][0])]
            report.append(f'URL check: {len(index["files"]) - len(bad)}/{len(index["files"])} reachable' + (f'; unreachable: {bad}' if bad else ''))
    return report


LINKS: dict[str, str] = {}
KEY_TITLES: dict[str, str] = {}  # gallery.json key -> gallery image title


def fill_slots(body: str, gallery: list[dict], server_url: str | None) -> str:
    """Replace ![...](gallery:NAME) slots with gallery image URLs, project links and the server zip link."""
    by_title = {g.get('title'): g.get('raw_url') or g['url'] for g in gallery if g.get('url')}  # url is a 350px preview
    def image(match):
        alt, name = match.group(1), match.group(2)
        url = by_title.get(KEY_TITLES.get(name, name))
        return f'![{alt}]({url})' if url else match.group(0)
    body = re.sub(r'!\[([^\]]*)\]\(gallery:([^)]+)\)', image, body)
    if server_url: body = body.replace(PLACEHOLDER_SERVER_URL, server_url)
    for token, url in LINKS.items(): body = body.replace(f']({token})', f']({url})')
    return body


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--dry-run', action='store_true', help='print every request without sending any; still builds the mrpack')
    ap.add_argument('--submit', action='store_true', help='submit both projects for review (otherwise they stay drafts)')
    ap.add_argument('--kofi', metavar='URL', help='Ko-fi page for the donation link on both projects')
    ap.add_argument('--server-zip-url', metavar='URL', help='page linking the server zip, e.g. the GitHub release page (Modrinth refuses direct download links)')
    ap.add_argument('--text-only', action='store_true', help='only re-sync titles, summaries and descriptions of the existing projects')
    ap.add_argument('--check-urls', action='store_true', help='also check every download URL in the final mrpack')
    ap.add_argument('--api', default='https://api.modrinth.com/v2', help='API base (staging: https://staging-api.modrinth.com/v2)')
    args = ap.parse_args()

    listing = json.loads((HERE / 'listing.json').read_text(encoding='utf-8'))
    version = listing['version']
    token = os.environ.get('MODRINTH_TOKEN')
    if not token and not args.dry_run: raise SystemExit('Set MODRINTH_TOKEN first (or use --dry-run).')
    if args.kofi and not re.fullmatch(r'https://ko-fi\.com/[A-Za-z0-9_]+/?', args.kofi): raise SystemExit('--kofi must look like https://ko-fi.com/NAME')
    api = Api(args.api, token, args.dry_run)

    mod_cfg, pack_cfg = listing['mod'], listing['modpack']
    LINKS.update(PACK_URL=f'https://modrinth.com/modpack/{pack_cfg["slug"]}', MOD_URL=f'https://modrinth.com/mod/{mod_cfg["slug"]}')
    icon = (HERE / 'icon.png').read_bytes()
    if len(icon) > 256 * 1024: raise SystemExit('icon.png is over the 256 KiB icon limit')
    gallery_cfg = json.loads((HERE / 'gallery.json').read_text(encoding='utf-8'))
    KEY_TITLES.update({g['key']: g['title'] for g in gallery_cfg})
    for g in gallery_cfg:
        size = (HERE / g['file']).stat().st_size
        if size > 5 * 1024 * 1024: raise SystemExit(f'{g["file"]} is over the 5 MiB gallery limit')
    changelog = (HERE / listing['changelog']).read_text(encoding='utf-8')
    jar_path = (HERE / fmt(mod_cfg['jar'], version)).resolve()
    jar = jar_path.read_bytes()
    template = (HERE / fmt(pack_cfg['template'], version)).resolve()
    output = HERE / fmt(pack_cfg['output'], version)
    if args.dry_run: output = output.with_name('DRY-RUN-' + output.name)  # placeholder project/version IDs inside
    common = {'game_versions': listing['game_versions'], 'loaders': listing['loaders'], 'version_type': listing['version_type'],
              'changelog': changelog, 'featured': True, 'status': 'listed', 'version_number': version}

    if args.text_only:
        for cfg, server in ((mod_cfg, None), (pack_cfg, args.server_zip_url)):
            project = api.request('GET', f'/project/{cfg["slug"]}')
            body = fill_slots((HERE / cfg['body']).read_text(encoding='utf-8'), project.get('gallery', []), server)
            if PLACEHOLDER_SERVER_URL in body: raise SystemExit('Pass --server-zip-url as well.')
            data = project_data(cfg, body)
            api.request('PATCH', f'/project/{project["id"]}', json_body={k: data[k] for k in ('title', 'description', 'body')})
            print(f'Updated the text of {cfg["slug"]}')
        return

    # 1. Core mod.
    mod_body = (HERE / mod_cfg['body']).read_text(encoding='utf-8')
    mod_project = ensure_project(api, mod_cfg, fill_slots(mod_body, [], None), icon, 'DRYMODID')
    mod_version = ensure_version(api, mod_project, dict(common, name=fmt(mod_cfg['version_name'], version), environment=mod_cfg['environment'],
                                 dependencies=[{'project_id': d['project_id'], 'version_id': d['version_id'], 'dependency_type': 'required'}
                                               for d in listing['core_dependencies']]),
                                 jar_path.name, jar, 'application/java-archive', 'DRYMODVER')
    core_file = next(f for f in mod_version['files'] if f.get('primary', True))
    if core_file['hashes']['sha512'] != hashlib.sha512(jar).hexdigest():
        raise SystemExit('The uploaded core mod version has a different file than dist/. Bump the version instead of reusing it.')
    if not args.dry_run:
        for attempt in range(10):
            if head_ok(core_file['url']): break
            time.sleep(3)
        else:
            raise SystemExit(f'Core mod file is not downloadable yet: {core_file["url"]}. Re-run in a minute.')
    print(f'Core mod file: {core_file["url"]}')

    # 2. Final mrpack.
    pack_bytes = build_mrpack(template, output, core_file)
    for line in validate_mrpack(output, args.check_urls): print(line)
    print(f'Final mrpack: {output.relative_to(HERE.parent)} ({len(pack_bytes)} bytes, sha512 {hashlib.sha512(pack_bytes).hexdigest()[:16]}...)')

    # 3. Modpack project and version.
    pack_body = (HERE / pack_cfg['body']).read_text(encoding='utf-8')
    pack_project = ensure_project(api, pack_cfg, fill_slots(pack_body, [], args.server_zip_url), icon, 'DRYPACKID')
    ensure_version(api, pack_project, dict(common, name=fmt(pack_cfg['version_name'], version), environment=pack_cfg['environment'], dependencies=[]),
                   output.name, pack_bytes, 'application/x-modrinth-modpack+zip', 'DRYPACKVER')
    print('Server zip: not uploaded to Modrinth (Content Rules 5.7 keep additional files for designated purposes).'
          f' Attach {fmt(pack_cfg["server_zip"], version)} to a GitHub release and pass --server-zip-url.')

    # 4. Gallery, description slots and donation links.
    for project, body, key in ((mod_project, mod_body, 'mod'), (pack_project, pack_body, 'modpack')):
        present = {g.get('title') for g in project.get('gallery', [])}
        for order, g in enumerate(gallery_cfg):
            if key not in g.get('projects', ['mod', 'modpack']) or g['title'] in present: continue
            api.request('POST', f'/project/{project["id"]}/gallery', raw=(HERE / g['file']).read_bytes(), content_type='image/png',
                        query={'ext': 'png', 'featured': str(bool(g.get('featured'))).lower(), 'title': g['title'],
                               'description': g['caption'], 'ordering': order})
        fresh = project if api.dry else api.request('GET', f'/project/{project["id"]}')
        filled = fill_slots(body, fresh.get('gallery', []), args.server_zip_url if key == 'modpack' else None)
        edit = {'body': filled}
        if args.kofi: edit['donation_urls'] = [{'id': 'ko-fi', 'platform': 'Ko-fi', 'url': args.kofi}]
        api.request('PATCH', f'/project/{project["id"]}', json_body=edit)
        if 'gallery:' in filled and not api.dry: print(f'Note: some screenshot slots in the {key} description had no matching gallery image.')

    # 5. Review.
    if args.submit:
        if PLACEHOLDER_SERVER_URL in fill_slots(pack_body, [], args.server_zip_url):
            raise SystemExit('Refusing to submit: pass --server-zip-url so the modpack description links the server download.')
        for project in (mod_project, pack_project):
            if project.get('status') in (None, 'draft', 'rejected'):
                api.request('PATCH', f'/project/{project["id"]}', json_body={'status': 'processing', 'requested_status': 'approved'})
                print(f'Submitted {project["id"]} for review.')
            else:
                print(f'{project["id"]} is already {project["status"]}; not resubmitted.')
    else:
        print('\nBoth projects stay drafts. Check them on modrinth.com, then re-run with --submit.')
    print(f'{api.count} API request(s) {"printed" if api.dry else "sent"}.')


if __name__ == '__main__':
    main()
