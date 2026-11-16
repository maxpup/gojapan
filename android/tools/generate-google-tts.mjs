import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, readFile, writeFile, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const androidRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(androidRoot, 'app/src/main/java/com/gojapan/feature/search/domain/TravelExpressionSearch.kt');
const output = path.join(androidRoot, 'app/src/main/assets/audio/expressions');
const projectId = process.argv[2];
if (!projectId || !/^[a-z][a-z0-9-]{4,62}$/.test(projectId)) {
  throw new Error('Usage: node android/tools/generate-google-tts.mjs PROJECT_ID');
}

const content = await readFile(source, 'utf8');
const expressions = [...content.matchAll(/TravelExpression\("([a-z0-9_]+)",\s*"([^"\\]+)",\s*"([^"\\]+)"/g)]
  .map(([, id, japanese, kana]) => ({ id, japanese, kana }));
if (expressions.length < 50 || new Set(expressions.map(({ id }) => id)).size !== expressions.length) {
  throw new Error('Could not safely read the reviewed expression list');
}

const kana = [...new Set(expressions.flatMap((item) => [...item.kana].filter((character) =>
  /[\u3040-\u30ff]/u.test(character) && character !== 'ー',
)))];
const clips = [
  ...expressions.map(({ id, japanese }) => ({ filename: `${id}.mp3`, text: japanese })),
  ...kana.map((character) => ({ filename: `kana_${character.codePointAt(0).toString(16)}.mp3`, text: character })),
];

if (process.argv.includes('--dry-run')) {
  console.log(`Would generate ${expressions.length} expressions and ${kana.length} kana clips`);
  process.exit(0);
}

const localGcloud = path.join(process.env.HOME, 'google-cloud-sdk/bin/gcloud');
const gcloud = process.env.GCLOUD_BIN || (existsSync(localGcloud) ? localGcloud : 'gcloud');
const token = execFileSync(gcloud, ['auth', 'print-access-token'], { encoding: 'utf8' }).trim();
await mkdir(output, { recursive: true });

let created = 0;
for (const { filename, text } of clips) {
  const target = path.join(output, filename);
  try {
    await access(target);
    continue;
  } catch { /* Generate missing clips only. */ }

  const response = await fetch('https://texttospeech.googleapis.com/v1/text:synthesize', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'x-goog-user-project': projectId,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      input: { text },
      voice: { languageCode: 'ja-JP', name: 'ja-JP-Standard-A' },
      audioConfig: { audioEncoding: 'MP3' },
    }),
  });
  const result = await response.json();
  if (!response.ok || !result.audioContent) {
    throw new Error(`Google TTS failed for ${filename}: ${response.status} ${JSON.stringify(result.error ?? result)}`);
  }
  await writeFile(target, Buffer.from(result.audioContent, 'base64'));
  created++;
  console.log(`Generated ${filename}`);
}
console.log(`Ready: ${clips.length} clips (${created} new), ja-JP-Standard-A`);
