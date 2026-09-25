import assert from 'node:assert/strict';
import { spawn, execFileSync } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

test('builds one project archive and returns its dist', async () => {
  const work = await mkdtemp(join(tmpdir(), 'vue-builder-test-'));
  const project = join(work, 'project');
  const source = join(work, 'source.zip');
  const output = join(work, 'dist.zip');
  let worker;
  try {
    await mkdir(project);
    await writeFile(join(project, 'package.json'), JSON.stringify({
      name: 'builder-smoke', version: '1.0.0',
      scripts: { build: 'node -e "require(\'fs\').mkdirSync(\'dist\'); require(\'fs\').writeFileSync(\'dist/index.html\', \'built\')"' },
    }));
    execFileSync('zip', ['-q', source, 'package.json'], { cwd: project });
    let url = process.env.VUE_BUILDER_TEST_URL;
    if (!url) {
      worker = spawn(process.execPath, [new URL('./vue-builder.mjs', import.meta.url).pathname], {
        env: { ...process.env, PORT: '0' }, stdio: ['ignore', 'pipe', 'pipe'],
      });
      const port = await new Promise((resolve, reject) => {
        worker.once('error', reject);
        worker.stdout.once('data', data => resolve(Number(/listening on (\d+)/.exec(data.toString())?.[1])));
      });
      assert.ok(port > 0);
      url = `http://127.0.0.1:${port}`;
    }
    const response = await fetch(`${url}/build`, {
      method: 'POST', body: await readFile(source), headers: { 'Content-Type': 'application/zip' },
    });
    assert.equal(response.status, 200);
    await writeFile(output, Buffer.from(await response.arrayBuffer()));
    assert.equal(execFileSync('unzip', ['-p', output, 'index.html']).toString(), 'built');
  } finally {
    worker?.kill();
    await rm(work, { recursive: true, force: true });
  }
});
