"""A blank GameTest harness, not a player-facing structure or island."""
from pathlib import Path
import gzip
import struct


def utf(s):
    data = s.encode()
    return struct.pack('>H', len(data)) + data


out = Path('src/main/resources/data/farskies/structure/empty.nbt')
out.parent.mkdir(parents=True, exist_ok=True)
data = b'\x0a' + utf('')
data += b'\x03' + utf('DataVersion') + struct.pack('>i', 3955)
data += b'\x09' + utf('size') + b'\x03' + struct.pack('>i3i', 3, 5, 5, 5)
data += b'\x09' + utf('palette') + b'\x0a' + struct.pack('>i', 1) + b'\x08' + utf('Name') + utf('minecraft:air') + b'\x00'
data += b'\x09' + utf('blocks') + b'\x0a' + struct.pack('>i', 0)
data += b'\x09' + utf('entities') + b'\x0a' + struct.pack('>i', 0) + b'\x00'
out.write_bytes(gzip.compress(data, mtime=0))
