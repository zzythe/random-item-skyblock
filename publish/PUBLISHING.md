# Publishing Random Item Skyblock 0.6.1 to Modrinth

Everything here is ready to upload. Nothing has been published yet.

## Files

| File | What it is |
|---|---|
| `upload.py` | Uploads everything with Modrinth API v2. Standard-library Python 3.10+. |
| `listing.json` | Slugs, titles, summaries, categories, licenses, version info, the core mod's dependencies. |
| `modpack-description.md`, `mod-description.md` | Project pages. `gallery:KEY` image slots, `PACK_URL`/`MOD_URL` links and `SERVER_ZIP_URL` are filled in by `upload.py`. |
| `changelog-0.6.1.md` | Version changelog (used for both projects). |
| `icon.png` | 512x512 icon for both projects (also inside the mod JAR). |
| `gallery/*.png`, `gallery.json` | 8 screenshots at 1920x1080 with titles and captions. `projects` says which project gets each one. |
| `verification/` | Server boot log, the full mrpack download and hash check, and the dry-run output. |
| `tools/` | `make_icon.py`, `boot_test.py` (server first-boot test), `check_mrpack.py` (download and hash every entry). |
| `dist-final/` | Created by `upload.py`: the final `.mrpack` that points at the uploaded core mod. |

Built inputs in `../dist/`: `random-item-skyblock-0.6.1.jar`, `Random-Item-Skyblock-0.6.1.mrpack-template` and `Random-Item-Skyblock-0.6.1-server.zip`.

## Steps

1. **Decide the name and slugs** in `listing.json`. The slug `random-item-skyblock` is taken by someone else's modpack, which also uses the title "Random Item Skyblock". A second, older pack has the same title too. The defaults are slug `random-item-skyblock-teams` with title "Random Item Skyblock", and slug `random-item-skyblock-core` for the mod. A more distinct title, such as "Random Item Skyblock: Teams", would help players find this pack in search.
2. **Create a GitHub release** (for example `v0.6.1` in a public repo) and attach `dist/Random-Item-Skyblock-0.6.1-server.zip`. Modrinth's content rules (5.7) only allow additional files for designated purposes such as source JARs, so the server zip is not uploaded to Modrinth. If you publish the source there, set `source_url` and `issues_url` in `listing.json`.
3. **Create a Modrinth token** (Settings > PATs) with: Create projects, Write projects, Read projects, Create versions, Read versions.
4. Dry run, then a real run, which leaves both projects as drafts:
   ```
   python publish/upload.py --dry-run --server-zip-url <release asset URL> [--kofi https://ko-fi.com/NAME]
   $env:MODRINTH_TOKEN = 'mrp_...'
   python publish/upload.py --server-zip-url <release asset URL> [--kofi https://ko-fi.com/NAME]
   ```
5. Check both draft pages on modrinth.com. Then submit them for review: `python publish/upload.py --server-zip-url <URL> --submit`. Re-running never creates duplicates.
6. To receive Modrinth's creator earnings, set up payouts in the Modrinth dashboard (Revenue). The Ko-fi link only appears if you pass `--kofi`.

## Tags and license used

- Modpack categories: multiplayer, adventure, technology (+ combat). Mod categories: game-mechanics, minigame, worldgen (+ economy, adventure). All of them come from `GET /v2/tag/category`.
- License: MIT for the core mod and the pack's own content.
- Modpack version loader: `neoforge`. Modrinth stores modpacks as the `mrpack` loader and adds every file in the pack index as an embedded dependency automatically.
