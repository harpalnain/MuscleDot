// Builds the app's built-in exercise list and pictures from selection.json.
//
//   cd tools/exercise-pack && npm install && node build-pack.mjs
//
// Outputs (committed, so the app build never needs Node or the network):
//   app/src/main/assets/exercises.json
//   app/src/main/assets/exercises/<id>_0.webp   start position
//   app/src/main/assets/exercises/<id>_1.webp   end position
//
// Photos come from https://github.com/yuhonas/free-exercise-db (Unlicense).
import { existsSync } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const SOURCE = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main';
const PICTURE_WIDTH = 400;
const WEBP_QUALITY = 70;
const PARALLEL = 8;

const here = path.dirname(fileURLToPath(import.meta.url));
const cacheDir = path.join(here, '.cache');
const assetsDir = path.resolve(here, '..', '..', 'app', 'src', 'main', 'assets');
const picturesDir = path.join(assetsDir, 'exercises');

async function download(url, file) {
  if (existsSync(file)) return;
  await fs.mkdir(path.dirname(file), { recursive: true });
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} for ${url}`);
  await fs.writeFile(file, Buffer.from(await response.arrayBuffer()));
}

async function inBatches(items, size, work) {
  for (let i = 0; i < items.length; i += size) {
    await Promise.all(items.slice(i, i + size).map(work));
  }
}

const selection = JSON.parse(await fs.readFile(path.join(here, 'selection.json'), 'utf8'));

const datasetFile = path.join(cacheDir, 'dataset.json');
await download(`${SOURCE}/dist/exercises.json`, datasetFile);
const dataset = new Map(JSON.parse(await fs.readFile(datasetFile, 'utf8')).map((e) => [e.id, e]));

const problems = [];
const seenIds = new Set();
for (const entry of selection) {
  if (seenIds.has(entry.id)) problems.push(`duplicate id: ${entry.id}`);
  seenIds.add(entry.id);
  const source = dataset.get(entry.source);
  if (!source) problems.push(`${entry.id}: source "${entry.source}" is not in the dataset`);
  else if (source.images.length < 2) problems.push(`${entry.id}: source "${entry.source}" has fewer than two photos`);
}
if (problems.length > 0) {
  console.error(problems.join('\n'));
  process.exit(1);
}

await fs.mkdir(picturesDir, { recursive: true });
const wanted = new Set();
const pictures = selection.flatMap((entry) => [0, 1].map((frame) => ({ entry, frame })));
await inBatches(pictures, PARALLEL, async ({ entry, frame }) => {
  const original = path.join(cacheDir, entry.source, `${frame}.jpg`);
  await download(`${SOURCE}/exercises/${encodeURIComponent(entry.source)}/${frame}.jpg`, original);
  const name = `${entry.id}_${frame}.webp`;
  wanted.add(name);
  await sharp(original).resize({ width: PICTURE_WIDTH }).webp({ quality: WEBP_QUALITY }).toFile(path.join(picturesDir, name));
});

for (const file of await fs.readdir(picturesDir)) {
  if (!wanted.has(file)) await fs.rm(path.join(picturesDir, file));
}

const list = selection
  .map(({ id, name, group, muscle, equipment, kind }) => ({ id, name, group, muscle, equipment, kind }))
  .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase(), 'en'));
const lines = list.map((entry) => '  ' + JSON.stringify(entry));
await fs.writeFile(path.join(assetsDir, 'exercises.json'), `[\n${lines.join(',\n')}\n]\n`);

console.log(`wrote ${list.length} exercises and ${wanted.size} pictures`);
