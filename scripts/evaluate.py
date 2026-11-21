"""Sends every sample to the service and scores the answer against samples/ground-truth.json.

    python scripts/evaluate.py                 # all documents
    python scripts/evaluate.py receipt-04      # only files whose name contains the argument
Uses only the standard library.
"""
import json
import mimetypes
import sys
import time
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
URL = "http://localhost:8080/extract"


def post_file(path):
    boundary = uuid.uuid4().hex
    ctype = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{path.name}\"\r\n"
            f"Content-Type: {ctype}\r\n\r\n").encode() + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(URL, data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(req, timeout=900) as resp:
        return json.load(resp)


def close(a, b):
    return a is not None and b is not None and abs(float(a) - float(b)) < 0.011


def norm(s):
    return "".join(ch for ch in (s or "").lower() if ch.isalnum())


def score(truth, got):
    inv = got["invoice"]
    checks = {
        "vendor": norm(truth["vendor"]) in norm(inv.get("vendor")) or norm(inv.get("vendor")) in norm(truth["vendor"]) and bool(inv.get("vendor")),
        "date": inv.get("date") == truth["date"],
        "currency": (inv.get("currency") or "").upper() == truth["currency"],
        "total": close(inv.get("total"), truth["total"]),
        "vat": close(inv.get("vat"), truth["vat"]),
        "items": len(inv.get("items") or []) == truth["items"],
    }
    if truth["invoiceNumber"]:
        checks["number"] = norm(inv.get("invoiceNumber")) == norm(truth["invoiceNumber"])
    return checks


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    truths = [t for t in json.loads((ROOT / "samples" / "ground-truth.json").read_text(encoding="utf-8")) if only in t["file"]]
    ok_fields = all_fields = flagged = 0
    for truth in truths:
        start = time.time()
        try:
            got = post_file(ROOT / "samples" / truth["file"])
        except Exception as e:  # noqa: BLE001
            print(f"{truth['file']:<40} FAILED: {e}")
            all_fields += 6
            continue
        checks = score(truth, got)
        ok_fields += sum(checks.values())
        all_fields += len(checks)
        wrong = [k for k, v in checks.items() if not v]
        warn = got.get("warnings") or []
        flagged += 1 if warn else 0
        print(f"{truth['file']:<40} {sum(checks.values())}/{len(checks)} fields  {time.time() - start:5.1f}s  "
              f"wrong: {', '.join(wrong) or '-'}  |  flagged by verifier: {'; '.join(warn) or '-'}")
    print(f"\nfield accuracy: {ok_fields}/{all_fields} ({100 * ok_fields / max(all_fields, 1):.0f}%), "
          f"documents flagged by the sum check: {flagged}/{len(truths)}")


if __name__ == "__main__":
    main()
