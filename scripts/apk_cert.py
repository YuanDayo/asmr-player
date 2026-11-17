#!/usr/bin/env python3
"""读取 APK 的签名证书指纹（SHA-256），本地文件或远端 URL 都行。

远端只取尾部 512KB（APK Signature Scheme v2 的证书就在 zip 末尾的签名块里），
不必为了核对签名去下整个 30MB 的包。

用法：
    python scripts/apk_cert.py app/build/outputs/apk/release/xxx.apk
    python scripts/apk_cert.py <apk 下载直链> --expect 0261c2ee...5500

退出码：0 = 匹配（或未指定 --expect），1 = 不匹配或读不到证书。
"""

from __future__ import annotations

import argparse
import hashlib
import struct
import sys
import urllib.request

TAIL = 512 * 1024
V2_BLOCK_ID = 0x7109871A


def _read_tail(path: str) -> bytes:
    if path.startswith(("http://", "https://")):
        req = urllib.request.Request(path, headers={"User-Agent": "kiite-apk-cert"})
        # 先问大小，再 Range 取尾部
        head = urllib.request.Request(path, method="HEAD", headers={"User-Agent": "kiite-apk-cert"})
        size = int(urllib.request.urlopen(head, timeout=30).headers["Content-Length"])
        start = max(0, size - TAIL)
        req.add_header("Range", "bytes=%d-" % start)
        return urllib.request.urlopen(req, timeout=60).read()
    with open(path, "rb") as f:
        f.seek(0, 2)
        size = f.tell()
        f.seek(max(0, size - TAIL))
        return f.read()


def cert_sha256(blob: bytes) -> str:
    magic = b"APK Sig Block 42"
    p = blob.rfind(magic)
    if p < 0:
        raise SystemExit("这不是 v2 签名的 APK（找不到签名块）")
    size = struct.unpack_from("<Q", blob, p - 8)[0]
    off = p - size + 16
    while off < p - 8:
        pair_len = struct.unpack_from("<Q", blob, off)[0]
        if pair_len == 0:
            break
        block_id = struct.unpack_from("<I", blob, off + 8)[0]
        if block_id == V2_BLOCK_ID:
            value = off + 12
            signed_data = value + 8
            digests_len = struct.unpack_from("<I", blob, signed_data + 4)[0]
            certs = signed_data + 8 + digests_len  # 证书段长度字段
            first_cert = certs + 4  # 第一张证书的长度字段
            cert_len = struct.unpack_from("<I", blob, first_cert)[0]
            der = blob[first_cert + 4: first_cert + 4 + cert_len]
            return hashlib.sha256(der).hexdigest()
        off += 8 + pair_len
    raise SystemExit("签名块里没有 v2 证书（可能是 v3-only 或未签名）")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("target", help="本地 APK 路径或下载直链")
    ap.add_argument("--expect", default="", help="期望的证书 SHA-256（可带冒号）")
    args = ap.parse_args()

    actual = cert_sha256(_read_tail(args.target))
    printed = ":".join(actual[i:i + 2] for i in range(0, len(actual), 2)).upper()
    print(f"cert SHA-256 = {printed}")
    if args.expect:
        want = args.expect.replace(":", "").lower()
        if actual != want:
            print(f"✗ 与期望值 {args.expect} 不一致", file=sys.stderr)
            return 1
        print("✓ 与期望值一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
