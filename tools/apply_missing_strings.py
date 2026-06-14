#!/usr/bin/env python3
"""Insert missing string keys into values-*/strings.xml from reference locales."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"


def parse_strings(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    out: dict[str, str] = {}
    for m in re.finditer(r'<string name="([^"]+)"(?: translatable="false")?>([\s\S]*?)</string>', text):
        if 'translatable="false"' in m.group(0).split(">", 1)[0]:
            continue
        out[m.group(1)] = m.group(2).strip()
    return out


def insert_missing(target: Path, additions: dict[str, str]) -> int:
    text = target.read_text(encoding="utf-8")
    existing = parse_strings(target)
    missing = {k: v for k, v in additions.items() if k not in existing}
    if not missing:
        return 0
    block = "\n".join(f'    <string name="{k}">{v}</string>' for k, v in missing.items())
    updated = text.replace("</resources>", f"\n{block}\n</resources>")
    target.write_text(updated, encoding="utf-8")
    return len(missing)


def main() -> None:
    base = parse_strings(RES / "values" / "strings.xml")
    refs = {
        name: parse_strings(RES / name / "strings.xml")
        for name in (
            "values-ar",
            "values-bg",
            "values-cs",
            "values-ru",
            "values-uk",
            "values-de",
            "values-fr",
            "values-es",
            "values-it",
            "values-pl",
            "values-nl",
            "values-pt",
            "values-ja",
            "values-ko",
            "values-zh-rCN",
            "values-tr",
        )
    }

    # Target locale -> reference locale (prefer linguistically close completed bundle)
    locale_ref = {
        "values-da": "values-de",
        "values-de": "values-de",
        "values-el": "values-bg",
        "values-es": "values-es",
        "values-es-rMX": "values-es",
        "values-et": "values-lv",  # filled below from lv after lv gets bg
        "values-fa-rIR": "values-ar",
        "values-fi": "values-de",
        "values-fil": "values-en",
        "values-fr": "values-fr",
        "values-ga": "values-en",
        "values-hi-rIN": "values-en",
        "values-hr": "values-cs",
        "values-hu": "values-pl",
        "values-in": "values-en",
        "values-it": "values-it",
        "values-ja": "values-ja",
        "values-ko": "values-ko",
        "values-lt": "values-lv",
        "values-lv": "values-bg",
        "values-mt": "values-it",
        "values-nl": "values-nl",
        "values-pl": "values-pl",
        "values-pt": "values-pt",
        "values-pt-rBR": "values-pt",
        "values-ro": "values-bg",
        "values-sk": "values-cs",
        "values-sl": "values-cs",
        "values-sv": "values-de",
        "values-th": "values-en",
        "values-tr": "values-tr",
        "values-vi": "values-en",
        "values-zh-rCN": "values-zh-rCN",
        "values-zh-rTW": "values-zh-rCN",
    }

    refs["values-en"] = base

    # Hand-authored bundles (filled by this script's data file on first run)
    hand_path = Path(__file__).with_name("missing_strings_hand.json")
    if hand_path.exists():
        import json

        hand = json.loads(hand_path.read_text(encoding="utf-8"))
        for locale, strings in hand.items():
            refs[locale] = {**refs.get(locale, base), **strings}

    needed_keys = sorted(base.keys() - {k for k, v in base.items() if False})
    # keys missing from typical locale
    sample = parse_strings(RES / "values-de" / "strings.xml")
    needed_keys = sorted(set(base.keys()) - set(sample.keys()) | {
        k for k in base if k.startswith("totp_") or "android_12" in k or k == "vpn_ip_list_ready_limited"
    })

    total = 0
    for locale, ref_name in locale_ref.items():
        ref = refs.get(ref_name, base)
        additions = {k: ref[k] for k in needed_keys if k in ref and k in base}
        # fallback to base English if ref lacks key
        for k in needed_keys:
            if k not in additions and k in base:
                additions[k] = base[k]
        n = insert_missing(RES / locale / "strings.xml", additions)
        if n:
            print(f"{locale}: +{n} from {ref_name}")
            total += n
    print(f"Done, inserted {total} strings")


if __name__ == "__main__":
    main()
