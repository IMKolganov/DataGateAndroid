#!/usr/bin/env node
/**
 * Fills missing string entries in each values-xx/strings.xml compared to values/strings.xml,
 * using Google Translate (unofficial web API via @vitalets/google-translate-api).
 *
 * Run: (cd tools && npm install && node translate_missing_strings.mjs)
 * Options: --dry-run  only report counts
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { translate } from "@vitalets/google-translate-api";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RES = path.resolve(__dirname, "../app/src/main/res");
const DRY = process.argv.includes("--dry-run");

/** values-* folder name -> Google Translate target code */
const FOLDER_TO_LANG = {
  "values-ar": "ar",
  "values-bg": "bg",
  "values-cs": "cs",
  "values-da": "da",
  "values-de": "de",
  "values-el": "el",
  "values-es": "es",
  "values-es-rMX": "es",
  "values-et": "et",
  "values-fa-rIR": "fa",
  "values-fi": "fi",
  "values-fil": "tl",
  "values-fr": "fr",
  "values-ga": "ga",
  "values-hi-rIN": "hi",
  "values-hr": "hr",
  "values-hu": "hu",
  "values-in": "id",
  "values-it": "it",
  "values-ja": "ja",
  "values-ko": "ko",
  "values-lt": "lt",
  "values-lv": "lv",
  "values-mt": "mt",
  "values-nl": "nl",
  "values-pl": "pl",
  "values-pt": "pt",
  "values-pt-rBR": "pt",
  "values-ro": "ro",
  "values-sk": "sk",
  "values-sl": "sl",
  "values-sv": "sv",
  "values-th": "th",
  "values-tr": "tr",
  "values-uk": "uk",
  "values-vi": "vi",
  "values-zh-rCN": "zh-CN",
  "values-zh-rTW": "zh-TW",
};

function parseStringsXml(xml) {
  const map = new Map();
  const re = /<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(xml))) {
    const name = m[1];
    const attrs = m[2];
    const body = m[3];
    if (attrs.includes('translatable="false"')) continue;
    map.set(name, body.trim());
  }
  return map;
}

function decodeAndroidStringBody(body) {
  let s = body.trim();
  s = s.replace(/\\n/g, "\n");
  s = s.replace(/\\'/g, "'");
  s = s.replace(/\\"/g, '"');
  s = s.replace(/\\\\/g, "\\");
  s = s.replace(/&lt;/g, "<");
  s = s.replace(/&gt;/g, ">");
  s = s.replace(/&amp;/g, "&");
  s = s.replace(/&quot;/g, '"');
  return s;
}

function encodeAndroidStringBody(raw) {
  let s = raw.replace(/\\/g, "\\\\");
  s = s.replace(/\n/g, "\\n");
  s = s.replace(/\r/g, "");
  s = s.replace(/'/g, "\\'");
  s = s.replace(/</g, "&lt;");
  s = s.replace(/>/g, "&gt;");
  s = s.replace(/&/g, "&amp;");
  return s;
}

async function translateText(text, to) {
  const delays = [200, 600, 1200, 2400, 4000];
  for (let i = 0; i < delays.length; i++) {
    try {
      const { text: out } = await translate(text, { from: "en", to });
      return out;
    } catch (e) {
      if (i === delays.length - 1) throw e;
      await new Promise((r) => setTimeout(r, delays[i]));
    }
  }
}

function listLocaleFolders() {
  return fs
    .readdirSync(RES)
    .filter((n) => n.startsWith("values-") && FOLDER_TO_LANG[n])
    .sort();
}

async function main() {
  const basePath = path.join(RES, "values", "strings.xml");
  const baseXml = fs.readFileSync(basePath, "utf8");
  const baseMap = parseStringsXml(baseXml);

  const folders = listLocaleFolders();
  let totalMissing = 0;

  for (const folder of folders) {
    const lang = FOLDER_TO_LANG[folder];
    const p = path.join(RES, folder, "strings.xml");
    const locXml = fs.readFileSync(p, "utf8");
    const locMap = parseStringsXml(locXml);
    const missing = [...baseMap.keys()].filter((k) => !locMap.has(k));
    if (missing.length === 0) continue;

    console.log(`${folder} (${lang}): ${missing.length} missing`);
    totalMissing += missing.length;

    if (DRY) continue;

    const additions = [];
    for (const key of missing) {
      const bodyEn = baseMap.get(key);
      const src = decodeAndroidStringBody(bodyEn);
      const out = await translateText(src, lang);
      await new Promise((r) => setTimeout(r, 2500));
      const enc = encodeAndroidStringBody(out);
      additions.push(`    <string name="${key}">${enc}</string>`);
    }

    const insert = "\n" + additions.join("\n") + "\n";
    if (!locXml.includes("</resources>")) {
      throw new Error(`${p}: no </resources>`);
    }
    const updated = locXml.replace(/<\/resources>\s*$/, `${insert}</resources>`);
    fs.writeFileSync(p, updated, "utf8");
  }

  console.log(DRY ? `Dry run: ${totalMissing} missing slot(s) across files` : `Done. Patched ${totalMissing} strings total.`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
