#!/usr/bin/env python3
"""Re-pack an APK so AndroidManifest.xml / resources.arsc are stored and 4-byte aligned."""
import struct
import sys
import zipfile


def align(src, dst, boundary=4):
    zin = zipfile.ZipFile(src)
    entries = []
    for info in zin.infolist():
        entries.append((info.filename, zin.read(info.filename),
                        info.compress_type == zipfile.ZIP_STORED, info.date_time))
    with zipfile.ZipFile(dst, 'w') as zout:
        for name, data, stored, date_time in entries:
            use_stored = stored or name in ('AndroidManifest.xml', 'resources.arsc')
            extra = b''
            if use_stored:
                pos = zout.fp.tell()
                pad = (-(pos + 30 + len(name.encode('utf-8')))) % boundary
                if pad:
                    pad += boundary
                    extra = struct.pack('<HH', 0xd935, pad - 4) + b'\x00' * (pad - 4)
            info = zipfile.ZipInfo(name, date_time)
            info.compress_type = zipfile.ZIP_STORED if use_stored else zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            info.extra = extra
            zout.writestr(info, data)


if __name__ == '__main__':
    align(sys.argv[1], sys.argv[2])
    for info in zipfile.ZipFile(sys.argv[2]).infolist():
        offset = info.header_offset + 30 + len(info.filename.encode('utf-8')) + len(info.extra)
        if info.compress_type == zipfile.ZIP_STORED and offset % 4:
            raise SystemExit('unaligned stored entry: ' + info.filename)
    print('aligned ok')
