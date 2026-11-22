"""Boot the server zip in a fresh empty folder through START-SERVER.cmd (Windows) and stop it at "Done".

Test harness only; the server pack itself needs no Python.
Usage: python publish/tools/boot_test.py dist/Random-Item-Skyblock-0.6.1-server.zip <empty-folder> [--answer yes|no]
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import time
import zipfile


def main():
    archive, target = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
    answer = sys.argv[sys.argv.index('--answer') + 1] if '--answer' in sys.argv else 'yes'
    reuse = '--reuse' in sys.argv  # second start in an already set-up folder: refresh only the scripts
    if reuse:
        with zipfile.ZipFile(archive) as z:
            for name in ('start.ps1', 'start.sh', 'START-SERVER.cmd'): z.extract(name, target)
    else:
        if target.exists() and any(target.iterdir()): raise SystemExit(f'{target} is not empty')
        target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as z: z.extractall(target)
    env = dict(os.environ)
    env.pop('JAVA_HOME', None)
    env['PATH'] = os.pathsep.join(p for p in env['PATH'].split(os.pathsep) if not (Path(p) / 'java.exe').exists())
    assert shutil.which('java', path=env['PATH']) is None, 'java must not be on PATH for this test'
    log = open(target.parent / (target.name + ('-restart' if reuse else '') + '-boot.log'), 'w', encoding='utf-8')
    start = time.time()
    proc = subprocess.Popen(['cmd.exe', '/c', str(target / 'START-SERVER.cmd')], cwd=target, env=env, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', bufsize=1)
    state = {'done': None, 'answered': False}

    def feed(text):
        proc.stdin.write(text + '\n'); proc.stdin.flush()

    def watchdog():
        time.sleep(1800)
        if proc.poll() is None: log.write('TIMEOUT\n'); proc.kill()
    threading.Thread(target=watchdog, daemon=True).start()
    buffer = ''
    while True:
        ch = proc.stdout.read(1)
        if not ch: break
        buffer += ch
        if 'Type yes to accept: ' in buffer and not state['answered']:
            state['answered'] = True; log.write(buffer); log.flush(); buffer = ''
            feed(answer)
        if 'Press any key to continue' in buffer and not proc.stdin.closed:
            log.write(buffer + '\n'); proc.stdin.close()
        if ch == '\n':
            line = buffer; buffer = ''
            log.write(f'[{time.time() - start:7.1f}s] {line}'); log.flush()
            if 'Done (' in line and 'For help, type "help"' in line and state['done'] is None:
                state['done'] = line.strip()
                feed('stop')
    proc.wait()
    log.close()
    print('exit', proc.returncode, 'elapsed', round(time.time() - start), 's')
    print('DONE LINE:', state['done'])
    return 0 if (state['done'] or answer != 'yes') else 1


if __name__ == '__main__': raise SystemExit(main())
