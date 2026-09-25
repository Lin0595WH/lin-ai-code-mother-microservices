import { createReadStream, createWriteStream } from 'node:fs';
import { mkdtemp, mkdir, lstat, readdir, rm, stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const MAX_ARCHIVE_BYTES = 100 * 1024 * 1024;
const env = { PATH: process.env.PATH, HOME: '/tmp', npm_config_cache: '/tmp/npm-cache', CI: 'true' };

async function run(command, args, cwd, timeoutMs) {
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, env, detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    for (const stream of [child.stdout, child.stderr]) {
      stream.on('data', chunk => { output = (output + chunk).slice(-4096); });
    }
    const timer = setTimeout(() => {
      try { process.kill(-child.pid, 'SIGKILL'); } catch { /* process already exited */ }
    }, timeoutMs);
    child.on('error', reject);
    child.on('close', code => {
      clearTimeout(timer);
      try { process.kill(-child.pid, 'SIGKILL'); } catch { /* no surviving children */ }
      if (code === 0) resolve();
      else reject(new Error(`${command} failed (${code}): ${output}`));
    });
  });
}

async function assertRegularTree(dir) {
  for (const name of await readdir(dir)) {
    const child = join(dir, name);
    const info = await lstat(child);
    if (info.isDirectory()) await assertRegularTree(child);
    else if (!info.isFile()) throw new Error('Build output contains a link or special file');
  }
}

let busy = false;
const server = createServer(async (request, response) => {
  if (request.method !== 'POST' || request.url !== '/build') {
    response.writeHead(404).end();
    return;
  }
  if (busy) {
    response.writeHead(503).end();
    return;
  }
  busy = true;
  let work;
  try {
    work = await mkdtemp(join(tmpdir(), 'vue-build-'));
    const source = join(work, 'source.zip');
    const project = join(work, 'project');
    const output = join(work, 'dist.zip');
    await mkdir(project);
    let received = 0;
    await pipeline(request, new Transform({
      transform(chunk, encoding, done) {
        received += chunk.length;
        done(received <= MAX_ARCHIVE_BYTES ? null : new Error('Source archive too large'), chunk);
      },
    }), createWriteStream(source, { flags: 'wx' }));
    await run('unzip', ['-qq', source, '-d', project], work, 30_000);
    if (!(await lstat(join(project, 'package.json'))).isFile()) throw new Error('package.json missing');
    await run('npm', ['install', '--no-audit', '--no-fund'], project, 300_000);
    await run('npm', ['run', 'build'], project, 180_000);
    const dist = join(project, 'dist');
    if (!(await lstat(join(dist, 'index.html'))).isFile()) throw new Error('dist/index.html missing');
    await assertRegularTree(dist);
    await run('zip', ['-q', '-r', output, '.'], dist, 60_000);
    const size = (await stat(output)).size;
    if (size > MAX_ARCHIVE_BYTES) throw new Error('Build output too large');
    response.writeHead(200, { 'Content-Type': 'application/zip', 'Content-Length': size });
    await pipeline(createReadStream(output), response);
  } catch (error) {
    console.error(error);
    if (!response.headersSent) response.writeHead(error.message?.includes('too large') ? 413 : 422).end();
    else response.destroy(error);
  } finally {
    try {
      if (work) await rm(work, { recursive: true, force: true });
    } catch (error) {
      console.error('Build directory cleanup failed:', error);
    }
    // One job per container also ends any npm descendants before another user's source arrives.
    server.close();
    setTimeout(() => process.exit(0), 100).unref();
  }
});

server.listen(Number(process.env.PORT ?? 8128), '0.0.0.0', () => {
  console.log(`Vue builder listening on ${server.address().port}`);
});
