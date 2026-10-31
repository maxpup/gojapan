import fs from "node:fs";
import vm from "node:vm";

const source = fs.readFileSync(new URL("../../index.html", import.meta.url), "utf8");
const groupsSource = source.match(/const groups=(\[[\s\S]*?\n    \]);/)?.[1];
const wordsSource = source.match(/const WORDS=(\{[\s\S]*?\n    \});/)?.[1];
if (!groupsSource || !wordsSource) throw new Error("Unable to find groups/WORDS in index.html");

const groups = vm.runInNewContext(`(${groupsSource})`);
const words = vm.runInNewContext(`(${wordsSource})`);
let order = 0;
const items = groups.flatMap(([row, entries], groupIndex) => entries.map(([hiragana, katakana, romaji, specialId]) => {
  const id = specialId ?? romaji;
  const [word, meaningZhCn] = words[id] ?? [hiragana, "对应读音"];
  const group = groupIndex <= 9 ? "SEION" : groupIndex <= 13 ? "DAKUON" : groupIndex === 14 ? "HANDAKUON" : "YOON";
  return {
    id,
    order: order++,
    group,
    row: row.replace(/・.*/, ""),
    hiragana,
    katakana,
    romaji,
    word,
    meaningZhCn,
    audioKey: `kana_${id.replaceAll("-", "_")}`,
  };
}));

const output = new URL("../app/src/main/assets/course/kana_v1.json", import.meta.url);
const baseline = new URL("../app/src/main/assets/course/kana_baseline_v1.json", import.meta.url);
fs.mkdirSync(new URL("../app/src/main/assets/course/", import.meta.url), { recursive: true });
const json = `${JSON.stringify({ version: 1, items }, null, 2)}\n`;
fs.writeFileSync(output, json);
fs.writeFileSync(baseline, json);
console.log(`Generated ${items.length} kana at ${output.pathname}`);
