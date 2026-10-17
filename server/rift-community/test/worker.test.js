import assert from 'node:assert/strict';
import { test } from 'node:test';

import { handle } from '../src/index.js';
import { gpuFamily, gpuKey, parseGameRun } from '../src/logic.js';
import { createD1 } from './d1.js';

const BASE = 'https://rift-community.example.workers.dev';

function env() {
  return { DB: createD1(), HASH_SALT: 'test-salt', ADMIN_TOKEN: 'admin-secret' };
}

function answer(overrides = {}) {
  return {
    gameName: 'Hollow Knight',
    gameStore: 'STEAM',
    storeGameId: '367520',
    deviceModel: 'NP05J',
    deviceManufacturer: 'nubia',
    gpuName: 'Adreno (TM) 830',
    socName: 'SM8750',
    androidVersion: '16',
    appVersion: '1.3.0',
    configs: { id: 'STEAM_367520', wineVersion: 'proton-10-arm64ec', dxwrapper: 'dxvk', drives: 'D:/storage/emulated/0' },
    rating: 5,
    tags: ['audio_glitches', 'not_a_tag'],
    notes: 'Smooth',
    avgFps: 58.5,
    sessionLengthSec: 1200,
    installId: 'install-0000000000000001',
    ...overrides,
  };
}

async function call(e, method, path, { body, headers = {}, now } = {}) {
  const request = new Request(`${BASE}${path}`, {
    method,
    headers: { 'content-type': 'application/json', 'cf-connecting-ip': '203.0.113.7', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const response = await handle(request, e, now);
  return { status: response.status, body: await response.json() };
}

test('a stored answer comes back in the GameNative compatibility shape', async () => {
  const e = env();
  const posted = await call(e, 'POST', '/api/game-run', { body: answer() });
  assert.equal(posted.status, 201);
  assert.equal(posted.body.created, true);

  const games = await call(e, 'GET', '/api/games/search?q=hollow%20knight');
  assert.deepEqual(games.body.games, [{ id: 1, name: 'Hollow Knight' }]);

  const devices = await call(e, 'GET', '/api/devices?model=nubia%20NP05J');
  assert.equal(devices.body.devices[0].model, 'nubia NP05J');
  assert.equal(devices.body.devices[0].androidVer, '16');

  const page = await call(e, 'GET', '/api/compatibility?gameId=1&sort=rating&dir=desc&page=0&limit=20&gpu=adreno');
  assert.equal(page.body.total, 1);
  const run = page.body.runs[0];
  assert.equal(run.rating, 5);
  assert.deepEqual(run.tags, ['audio_glitches']);
  assert.equal(run.gameStore, 'steam');
  assert.equal(run.device.gpu, 'Adreno (TM) 830');
  // Only the keys GameNative's client accepts are kept: no container id, no drive paths.
  assert.deepEqual(run.configs, { wineVersion: 'proton-10-arm64ec', dxwrapper: 'dxvk' });
});

test('the same install answering again for the same setup replaces its answer', async () => {
  const e = env();
  const now = Date.parse('2026-10-17T12:00:00Z');
  await call(e, 'POST', '/api/game-run', { body: answer(), now });
  const again = await call(e, 'POST', '/api/game-run', { body: answer({ rating: 2 }), now: now + 60_000 });
  assert.equal(again.body.created, false);
  const page = await call(e, 'GET', '/api/compatibility?gameId=1');
  assert.equal(page.body.total, 1);
  assert.equal(page.body.runs[0].rating, 2);

  const other = await call(e, 'POST', '/api/game-run', { body: answer({ installId: 'install-0000000000000002' }), now });
  assert.equal(other.body.created, true);
});

test('bad answers are refused', async () => {
  const e = env();
  assert.equal((await call(e, 'POST', '/api/game-run', { body: answer({ rating: 9 }) })).status, 400);
  assert.equal((await call(e, 'POST', '/api/game-run', { body: answer({ installId: 'short' }) })).status, 400);
  assert.equal((await call(e, 'POST', '/api/game-run', { body: answer({ configs: { drives: 'x' } }) })).status, 400);
  assert.equal((await call(e, 'POST', '/api/game-run', { body: answer({ gameName: '' }) })).status, 400);
});

test('one install is limited per hour', async () => {
  const e = env();
  const now = Date.parse('2026-10-17T12:00:00Z');
  let last;
  for (let i = 0; i < 31; i++) {
    last = await call(e, 'POST', '/api/game-run', {
      body: answer({ gameName: `Game ${i}` }),
      headers: { 'cf-connecting-ip': `198.51.100.${i}` },
      now,
    });
  }
  assert.equal(last.status, 429);
});

test('best config prefers the same GPU, then the Adreno family, and skips failed runs', async () => {
  const e = env();
  const post = (overrides) => call(e, 'POST', '/api/game-run', { body: answer(overrides) });
  await post({ installId: 'install-a-000000000000', gpuName: 'Adreno (TM) 750', configs: { wineVersion: 'family' }, rating: 5 });
  await post({ installId: 'install-b-000000000000', gpuName: 'Adreno (TM) 830', configs: { wineVersion: 'broken' }, rating: 5, tags: ['does_not_open'] });
  const best = (gpuName) => call(e, 'POST', '/api/best-config', { body: { gameName: 'hollow knight', gpuName, game_store: 'STEAM' } });

  let result = await best('Adreno (TM) 740');
  assert.equal(result.body.matchType, 'gpu_family_match');
  assert.deepEqual(result.body.bestConfig, { wineVersion: 'family' });

  await post({ installId: 'install-c-000000000000', gpuName: 'Adreno (TM) 740', configs: { wineVersion: 'exact' }, rating: 4 });
  result = await best('Adreno (TM) 740');
  assert.equal(result.body.matchType, 'exact_gpu_match');
  assert.deepEqual(result.body.bestConfig, { wineVersion: 'exact' });

  result = await best('Mali-G720');
  assert.equal(result.body.matchType, 'fallback_match');

  result = await best('');
  assert.equal(result.body.matchType, 'fallback_match');
  result = await call(e, 'POST', '/api/best-config', { body: { gameName: 'Unknown Game', gpuName: 'Adreno 740' } });
  assert.equal(result.body.matchType, 'no_match');
  assert.deepEqual(result.body.bestConfig, {});
});

test('moderation needs the admin token and hides runs from everyone', async () => {
  const e = env();
  await call(e, 'POST', '/api/game-run', { body: answer() });
  assert.equal((await call(e, 'POST', '/admin/runs/1/hide')).status, 401);
  const auth = { authorization: 'Bearer admin-secret' };
  assert.equal((await call(e, 'POST', '/admin/runs/1/hide', { headers: auth })).body.hidden, true);
  assert.equal((await call(e, 'GET', '/api/compatibility?gameId=1')).body.total, 0);
  assert.equal((await call(e, 'GET', '/admin/runs', { headers: auth })).body.runs[0].hidden, true);
  assert.equal((await call(e, 'DELETE', '/admin/runs/1', { headers: auth })).body.deleted, true);
});

test('GPU names normalise like the app does', () => {
  assert.equal(gpuKey('Adreno (TM) 830'), 'adreno:830');
  assert.equal(gpuFamily('Adreno (TM) 830'), 'adreno_elite');
  assert.equal(gpuFamily('Adreno 740'), 'adreno_standard');
  assert.equal(gpuKey('Mali-G720'), 'arm:g720');
  assert.equal(parseGameRun(answer({ configs: JSON.stringify({ wineVersion: 'w' }) })).config.wineVersion, 'w');
});
