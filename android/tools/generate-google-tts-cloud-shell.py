"""Generate bundled Google Cloud Japanese TTS clips from reviewed expressions."""

import argparse
import base64
import json
import re
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/gojapan/feature/search/domain/TravelExpressionSearch.kt"
OUTPUT = ROOT / "app/src/main/assets/audio/expressions"
VOICE = "ja-JP-Standard-A"


def clips():
    source = SOURCE.read_text(encoding="utf-8")
    expressions = re.findall(
        r'TravelExpression\("([a-z0-9_]+)",\s*"([^"\\]+)",\s*"([^"\\]+)"', source
    )
    if len(expressions) < 50 or len({item[0] for item in expressions}) != len(expressions):
        raise RuntimeError("Could not safely read the reviewed expression list")
    result = [(f"{item_id}.mp3", japanese) for item_id, japanese, _ in expressions]
    kana = dict.fromkeys(
        character
        for _, _, reading in expressions
        for character in reading
        if ("\u3040" <= character <= "\u30ff") and character != "ー"
    )
    result.extend((f"kana_{ord(character):x}.mp3", character) for character in kana)
    return result, len(expressions), len(kana)


def synthesize(token, project_id, text):
    payload = json.dumps(
        {
            "input": {"text": text},
            "voice": {"languageCode": "ja-JP", "name": VOICE},
            "audioConfig": {"audioEncoding": "MP3"},
        },
        ensure_ascii=False,
    ).encode("utf-8")
    request = urllib.request.Request(
        "https://texttospeech.googleapis.com/v1/text:synthesize",
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "x-goog-user-project": project_id,
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    for attempt in range(8):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = json.load(response)
            return base64.b64decode(body["audioContent"])
        except urllib.error.HTTPError as error:
            detail = error.read().decode()
            transient = error.code in (429, 500, 503) or (
                error.code == 403 and "SERVICE_DISABLED" in detail
            )
            if not transient or attempt == 7:
                raise RuntimeError(f"Google TTS returned HTTP {error.code}: {detail}") from error
            print(f"Google TTS temporarily unavailable; retry {attempt + 1}/8", flush=True)
            time.sleep(15)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("project_id")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,62}", args.project_id):
        parser.error("invalid project ID")
    items, expression_count, kana_count = clips()
    print(f"Found {expression_count} expressions and {kana_count} kana clips", flush=True)
    if args.dry_run:
        return
    token = subprocess.check_output(
        ["gcloud", "auth", "print-access-token"], text=True
    ).strip()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    created = 0
    for filename, text in items[: args.limit]:
        target = OUTPUT / filename
        if target.exists():
            continue
        data = synthesize(token, args.project_id, text)
        temporary = target.with_suffix(".tmp")
        temporary.write_bytes(data)
        temporary.replace(target)
        created += 1
        print(f"Generated {filename}", flush=True)
    print(f"Ready: {created} new clips", flush=True)


if __name__ == "__main__":
    main()
