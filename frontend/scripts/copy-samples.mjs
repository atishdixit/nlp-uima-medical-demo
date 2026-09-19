// Copies the repository's shared sample charts (../samples/*.txt) into public/samples so the UI can offer them.
// Angular cannot bundle assets from outside its workspace, and one copy of each chart keeps the UI, the
// backend tests and the docs in sync. The copy is generated (git-ignored); this runs before build/start.
import { copyFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(here, '..', '..', 'samples');
const target = resolve(here, '..', 'public', 'samples');

if (!existsSync(source)) {
  console.warn(`copy-samples: ${source} not found, the sample picker will be empty`);
  process.exit(0);
}

mkdirSync(target, { recursive: true });
const files = readdirSync(source).filter((f) => f.endsWith('.txt'));
for (const file of files) {
  copyFileSync(join(source, file), join(target, file));
}
console.log(`copy-samples: ${files.length} sample chart(s) -> public/samples`);
