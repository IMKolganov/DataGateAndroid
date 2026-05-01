#!/usr/bin/env python3
"""
Merge translated string bundles from Cursor subagent JSONL transcripts into
app/src/main/res/values-*/strings.xml (appends missing keys before </resources>).

Transcripts (parent chat subagents):
  addf94d6-*  → bg..fi
  c8cf54ae-*  → fr..sk + sl/sv (truncated batch + completion)
  0d35346e-*  → in, ja, ko, th, tr, vi, zh-rCN, zh-rTW
  2521e5e8-*  → fa-rIR, fil, ga, hi-rIN, mt, uk

Run from repo root:
  python3 tools/apply_i18n_from_agent_transcripts.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
def _transcript_subagents_dir() -> Path | None:
    base = (
        Path.home()
        / ".cursor"
        / "projects"
        / "home-imkolganov-Android-DataGateAndroid"
        / "agent-transcripts"
    )
    if not base.is_dir():
        return None
    preferred = base / "d427fae1-6117-422c-b659-932d7c8dbe49" / "subagents"
    if preferred.is_dir():
        return preferred
    candidates = sorted(base.glob("*/subagents"), key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0] if candidates else None


def _parse_jsonl(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def _strip_trailing_redacted(t: str) -> str:
    # Do not split on [REDACTED] mid-string (breaks JSON); only strip a trailing redaction marker.
    return re.sub(r"\n\n\[REDACTED\]\s*$", "", t).strip()


def _assistant_text_blocks(obj: dict) -> list[str]:
    if obj.get("role") != "assistant":
        return []
    out: list[str] = []
    for block in obj.get("message", {}).get("content") or []:
        if isinstance(block, dict) and block.get("type") == "text":
            t = _strip_trailing_redacted(block.get("text") or "")
            if t:
                out.append(t)
    return out


def _strip_md_json_fence(s: str) -> str:
    s = s.strip()
    if s.startswith("```"):
        s = re.sub(r"^```[a-zA-Z]*\n?", "", s)
        s = s.split("```", 1)[0]
    return s.strip()


def _try_parse_top_locale_dict(s: str) -> dict | None:
    s = _strip_md_json_fence(s)
    if not s.startswith("{"):
        i = s.find('{"values-')
        if i == -1:
            i = s.find("\n{")
            if i != -1:
                i = s.find("{", i)
        if i == -1:
            return None
        s = s[i:]
    dec = json.JSONDecoder()
    try:
        obj, _ = dec.raw_decode(s)
    except json.JSONDecodeError:
        cut = s.find(',"values-sl":')
        if cut == -1:
            return None
        frag = s[:cut] + "}"
        try:
            obj = json.loads(frag)
        except json.JSONDecodeError:
            return None
    if not isinstance(obj, dict):
        return None
    if not any(isinstance(k, str) and k.startswith("values-") for k in obj):
        return None
    return obj


def _merge_transcript_file(path: Path, into: dict[str, dict[str, str]]) -> None:
    if not path.is_file():
        print(f"WARN: missing transcript {path.name}", file=sys.stderr)
        return
    for obj in _parse_jsonl(path):
        for seg in _assistant_text_blocks(obj):
            d = _try_parse_top_locale_dict(seg)
            if not d:
                continue
            top_keys = [k for k in d if isinstance(k, str) and k.startswith("values-")]
            if len(top_keys) == 2 and set(top_keys) <= {"values-sl", "values-sv"}:
                for k in top_keys:
                    if isinstance(d[k], dict) and d[k]:
                        into[k] = d[k]
                continue
            for k, v in d.items():
                if not (isinstance(k, str) and k.startswith("values-")):
                    continue
                if not isinstance(v, dict) or not v:
                    continue
                into[k] = v


def escape_android_string_value(raw: str) -> str:
    s = raw.replace("\\", "\\\\")
    s = s.replace("\n", "\\n").replace("\r", "")
    s = s.replace("'", "\\'")
    s = s.replace("&", "&amp;")
    s = s.replace("<", "&lt;")
    s = s.replace(">", "&gt;")
    return s


def apply_folder(folder: str, kv: dict[str, str]) -> None:
    path = RES / folder / "strings.xml"
    if not path.is_file():
        print(f"SKIP missing {path}", file=sys.stderr)
        return
    xml = path.read_text(encoding="utf-8")
    existing = set(re.findall(r'<string\s+name="([^"]+)"', xml))
    to_add = [(k, v) for k, v in sorted(kv.items()) if k not in existing]
    if not to_add:
        print(f"OK {folder}: complete")
        return
    block = "\n" + "\n".join(
        f'    <string name="{k}">{escape_android_string_value(v)}</string>' for k, v in to_add
    ) + "\n"
    if "</resources>" not in xml:
        print(f"ERR no </resources> {path}", file=sys.stderr)
        return
    path.write_text(xml.replace("</resources>", f"{block}</resources>", 1), encoding="utf-8")
    print(f"PATCH {folder}: +{len(to_add)}")


def _fixes(bundle: dict[str, dict[str, str]]) -> None:
    if "values-mt" in bundle:
        v = bundle["values-mt"].get("auth_field_new_password", "")
        if "ŻPassword" in v or v.startswith("Ż"):
            bundle["values-mt"]["auth_field_new_password"] = "Password ġdida"
    if "values-hi-rIN" in bundle:
        s = bundle["values-hi-rIN"].get("error_google_account_reauth_failed", "")
        if any("\u0400" <= ch <= "\u04ff" for ch in s):
            bundle["values-hi-rIN"]["error_google_account_reauth_failed"] = (
                "Google खाता पुनः प्रमाणीकरण विफल। इस डिवाइस पर Google खाता जाँचें, "
                "Google Play सेवाएँ अपडेट करें, फिर पुनः साइन इन करने का प्रयास करें।"
            )


def main() -> None:
    transcripts = _transcript_subagents_dir()
    if not transcripts or not transcripts.is_dir():
        print("No agent transcript subagents directory found under ~/.cursor/projects/…", file=sys.stderr)
        sys.exit(1)

    bundle: dict[str, dict[str, str]] = {}
    for pattern in (
        "addf94d6-*.jsonl",
        "c8cf54ae-*.jsonl",
        "0d35346e-*.jsonl",
        "2521e5e8-*.jsonl",
    ):
        for p in sorted(transcripts.glob(pattern)):
            _merge_transcript_file(p, bundle)

    _fixes(bundle)

    for folder in sorted(bundle):
        if folder.startswith("values-"):
            apply_folder(folder, bundle[folder])


if __name__ == "__main__":
    main()
