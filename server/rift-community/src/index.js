// Rift community server: stores "How did the game run?" answers from Rift and serves them back
// as community configs. Routes and JSON shapes follow api.gamenative.app, so the app's
// CommunityConfigService, BestConfigService and GameRunApi parsers work unchanged against it.
//
//   POST /api/game-run          one answer (GameRunApi body + installId, gameStore, storeGameId)
//   GET  /api/games/search?q=   {games: [{id, name}]}
//   GET  /api/devices?model=    {devices: [{id, model, gpu, androidVer, soc}]}
//   GET  /api/compatibility     {runs, page, pageSize, total}; gameId, sort, page, limit, deviceId | gpu
//   POST /api/best-config       {bestConfig, matchType, matchedGpu, matchedDeviceId, matchedStore}
//   GET  /admin/runs, POST /admin/runs/:id/hide|unhide, DELETE /admin/runs/:id  (Bearer ADMIN_TOKEN)

import {
  BadRequest,
  LIMITS,
  configFingerprint,
  deviceName,
  gameStore,
  gpuKey,
  modelKey,
  nameKey,
  parseGameRun,
  pickBestConfig,
  sha256Hex,
} from './logic.js';

const JSON_HEADERS = { 'content-type': 'application/json; charset=utf-8' };

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: { ...JSON_HEADERS, ...headers } });
}

function error(status, message, headers = {}) {
  return json({ error: { message } }, status, headers);
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > LIMITS.bodyBytes) throw new BadRequest('body is too large');
  try {
    return JSON.parse(text);
  } catch {
    throw new BadRequest('body is not JSON');
  }
}

function intParam(url, name, fallback, min, max) {
  const value = Number.parseInt(url.searchParams.get(name) ?? '', 10);
  if (!Number.isFinite(value)) return fallback;
  return Math.min(Math.max(value, min), max);
}

/** Counts one request in the hour's bucket; false once the limit is reached. */
async function allow(env, kind, key, limit, now) {
  const hour = Math.floor(now / 3_600_000);
  const bucket = `${kind}:${key}:${hour}`;
  const row = await env.DB.prepare(
    `INSERT INTO rate (bucket, count, expires_at) VALUES (?1, 1, ?2)
     ON CONFLICT (bucket) DO UPDATE SET count = count + 1
     RETURNING count`,
  )
    .bind(bucket, (hour + 1) * 3_600_000)
    .first();
  return (row?.count ?? 1) <= limit;
}

async function upsertGame(env, name) {
  const key = nameKey(name);
  await env.DB.prepare('INSERT INTO games (name, name_key) VALUES (?1, ?2) ON CONFLICT (name_key) DO NOTHING')
    .bind(name, key)
    .run();
  return env.DB.prepare('SELECT id FROM games WHERE name_key = ?1').bind(key).first('id');
}

async function upsertDevice(env, run) {
  const model = deviceName(run.manufacturer, run.model);
  const key = modelKey(model);
  await env.DB.prepare(
    `INSERT INTO devices (manufacturer, model, model_key, gpu, gpu_key, soc, android_ver)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
     ON CONFLICT (model_key, gpu, soc, android_ver) DO NOTHING`,
  )
    .bind(run.manufacturer, model, key, run.gpu, gpuKey(run.gpu), run.soc, run.androidVersion)
    .run();
  return env.DB.prepare(
    'SELECT id FROM devices WHERE model_key = ?1 AND gpu = ?2 AND soc = ?3 AND android_ver = ?4',
  )
    .bind(key, run.gpu, run.soc, run.androidVersion)
    .first('id');
}

async function submitRun(request, env, now) {
  const run = parseGameRun(await readJson(request));
  const salt = env.HASH_SALT ?? '';
  const installHash = await sha256Hex(`install:${run.installId}:${salt}`);
  const address = request.headers.get('cf-connecting-ip') ?? '';
  const addressHash = await sha256Hex(`address:${address}:${salt}`);
  if (
    !(await allow(env, 'install', installHash, LIMITS.runsPerInstallPerHour, now)) ||
    !(await allow(env, 'address', addressHash, LIMITS.runsPerAddressPerHour, now))
  ) {
    return error(429, 'Too many answers, try again later', { 'retry-after': '3600' });
  }

  const gameId = await upsertGame(env, run.gameName);
  const deviceId = await upsertDevice(env, run);
  const configs = JSON.stringify(run.config);
  const configHash = await sha256Hex(configFingerprint(run.config));
  const createdAt = new Date(now).toISOString();
  const values = [
    run.rating,
    JSON.stringify(run.tags),
    run.notes,
    run.avgFps,
    run.sessionLengthSec,
    run.appVersion,
    createdAt,
  ];

  // The same install answering again for the same game and setup right away corrects its answer.
  const repeat = await env.DB.prepare(
    `SELECT id FROM runs
     WHERE install_hash = ?1 AND game_id = ?2 AND config_hash = ?3 AND created_at >= ?4
     ORDER BY id DESC LIMIT 1`,
  )
    .bind(installHash, gameId, configHash, new Date(now - LIMITS.repeatWindowMs).toISOString())
    .first('id');
  if (repeat) {
    await env.DB.prepare(
      `UPDATE runs SET rating = ?1, tags = ?2, notes = ?3, avg_fps = ?4, session_length_sec = ?5,
       app_version = ?6, created_at = ?7 WHERE id = ?8`,
    )
      .bind(...values, repeat)
      .run();
    return json({ id: repeat, created: false });
  }

  const result = await env.DB.prepare(
    `INSERT INTO runs (rating, tags, notes, avg_fps, session_length_sec, app_version, created_at,
       game_id, device_id, configs, config_hash, game_store, store_game_id, install_hash)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)`,
  )
    .bind(...values, gameId, deviceId, configs, configHash, run.gameStore, run.storeGameId, installHash)
    .run();
  return json({ id: result.meta.last_row_id, created: true }, 201);
}

async function searchGames(url, env) {
  const key = nameKey(url.searchParams.get('q') ?? '');
  if (!key) return json({ games: [] });
  const { results } = await env.DB.prepare(
    `SELECT id, name FROM games WHERE name_key = ?1 OR name_key LIKE ?2 ESCAPE '\\'
     ORDER BY name_key = ?1 DESC, length(name_key) LIMIT 20`,
  )
    .bind(key, `%${key.replace(/[\\%_]/g, (c) => `\\${c}`)}%`)
    .all();
  return json({ games: results });
}

function deviceJson(row) {
  return { id: row.id, model: row.model, gpu: row.gpu, androidVer: row.android_ver, soc: row.soc };
}

async function searchDevices(url, env) {
  const key = modelKey(url.searchParams.get('model') ?? '');
  if (!key) return json({ devices: [] });
  const { results } = await env.DB.prepare(
    `SELECT id, model, gpu, android_ver, soc FROM devices
     WHERE model_key = ?1 OR model_key LIKE ?2 ORDER BY model_key = ?1 DESC, id DESC LIMIT 50`,
  )
    .bind(key, `%${key}%`)
    .all();
  return json({ devices: results.map(deviceJson) });
}

function runJson(row) {
  return {
    id: row.id,
    rating: row.rating,
    avgFps: row.avg_fps,
    tags: JSON.parse(row.tags),
    notes: row.notes,
    configs: JSON.parse(row.configs),
    createdAt: row.created_at,
    appVersion: row.app_version,
    sessionLengthSec: row.session_length_sec,
    gameStore: row.game_store,
    deviceId: row.device_id,
    device: deviceJson({ ...row, id: row.device_id }),
  };
}

const RUN_COLUMNS = `r.id, r.rating, r.avg_fps, r.tags, r.notes, r.configs, r.created_at, r.app_version,
  r.session_length_sec, r.game_store, r.device_id, d.model, d.gpu, d.android_ver, d.soc`;

async function compatibility(url, env) {
  const gameId = intParam(url, 'gameId', 0, 0, Number.MAX_SAFE_INTEGER);
  if (gameId <= 0) throw new BadRequest('gameId is required');
  const page = intParam(url, 'page', 0, 0, 10_000);
  const pageSize = intParam(url, 'limit', 20, 1, LIMITS.pageSize);
  const sortColumn = url.searchParams.get('sort') === 'created_at' ? 'r.created_at' : 'r.rating';
  const direction = url.searchParams.get('dir') === 'asc' ? 'ASC' : 'DESC';
  const deviceId = intParam(url, 'deviceId', 0, 0, Number.MAX_SAFE_INTEGER);
  const gpu = (url.searchParams.get('gpu') ?? '').trim().toLowerCase();

  let where = 'r.game_id = ?1 AND r.hidden = 0';
  const binds = [gameId];
  if (deviceId > 0) {
    where += ' AND r.device_id = ?2';
    binds.push(deviceId);
  } else if (gpu) {
    where += ' AND lower(d.gpu) LIKE ?2';
    binds.push(`%${gpu}%`);
  }
  const from = 'FROM runs r JOIN devices d ON d.id = r.device_id';
  const total = await env.DB.prepare(`SELECT count(*) AS total ${from} WHERE ${where}`)
    .bind(...binds)
    .first('total');
  const { results } = await env.DB.prepare(
    `SELECT ${RUN_COLUMNS} ${from} WHERE ${where}
     ORDER BY ${sortColumn} ${direction}, r.created_at ${direction}, r.id ${direction}
     LIMIT ${pageSize} OFFSET ${page * pageSize}`,
  )
    .bind(...binds)
    .all();
  return json({ runs: results.map(runJson), page, pageSize, total }, 200, { 'cache-control': 'public, max-age=120' });
}

async function bestConfig(request, env) {
  const body = await readJson(request);
  const key = nameKey(typeof body?.gameName === 'string' ? body.gameName : '');
  const gpuName = typeof body?.gpuName === 'string' ? body.gpuName : '';
  const store = body?.game_store ?? body?.gameStore ?? '';
  const noMatch = { bestConfig: {}, matchType: 'no_match', matchedGpu: '', matchedDeviceId: 0, matchedStore: gameStore(store) };
  if (!key) return json(noMatch);
  const { results } = await env.DB.prepare(
    `SELECT r.configs, r.rating, r.tags, r.avg_fps, r.created_at, r.game_store, r.device_id, d.gpu AS device_gpu
     FROM runs r JOIN games g ON g.id = r.game_id JOIN devices d ON d.id = r.device_id
     WHERE g.name_key = ?1 AND r.hidden = 0 AND r.rating >= 4
     ORDER BY r.created_at DESC LIMIT 500`,
  )
    .bind(key)
    .all();
  const { matchType, run } = pickBestConfig(
    results.map((row) => ({ ...row, tags: JSON.parse(row.tags) })),
    gpuName,
    store,
  );
  if (!run) return json(noMatch);
  return json({
    bestConfig: JSON.parse(run.configs),
    matchType,
    matchedGpu: run.device_gpu,
    matchedDeviceId: run.device_id,
    matchedStore: run.game_store,
  });
}

async function admin(request, url, env) {
  const token = env.ADMIN_TOKEN ?? '';
  if (!token || request.headers.get('authorization') !== `Bearer ${token}`) return error(401, 'Unauthorized');
  if (request.method === 'GET' && url.pathname === '/admin/runs') {
    const limit = intParam(url, 'limit', 50, 1, 200);
    const { results } = await env.DB.prepare(
      `SELECT ${RUN_COLUMNS}, g.name AS game, r.hidden FROM runs r
       JOIN devices d ON d.id = r.device_id JOIN games g ON g.id = r.game_id
       ORDER BY r.id DESC LIMIT ${limit}`,
    ).all();
    return json({ runs: results.map((row) => ({ ...runJson(row), game: row.game, hidden: row.hidden === 1 })) });
  }
  const match = url.pathname.match(/^\/admin\/runs\/(\d+)(?:\/(hide|unhide))?$/);
  if (!match) return error(404, 'Not found');
  const id = Number(match[1]);
  if (request.method === 'DELETE' && !match[2]) {
    await env.DB.prepare('DELETE FROM runs WHERE id = ?1').bind(id).run();
    return json({ id, deleted: true });
  }
  if (request.method === 'POST' && match[2]) {
    await env.DB.prepare('UPDATE runs SET hidden = ?1 WHERE id = ?2').bind(match[2] === 'hide' ? 1 : 0, id).run();
    return json({ id, hidden: match[2] === 'hide' });
  }
  return error(405, 'Method not allowed');
}

export async function handle(request, env, now = Date.now()) {
  const url = new URL(request.url);
  const route = `${request.method} ${url.pathname.replace(/\/+$/, '') || '/'}`;
  try {
    switch (route) {
      case 'GET /':
        return json({ name: 'rift-community', ok: true });
      case 'POST /api/game-run':
        return await submitRun(request, env, now);
      case 'GET /api/games/search':
        return await searchGames(url, env);
      case 'GET /api/devices':
        return await searchDevices(url, env);
      case 'GET /api/compatibility':
        return await compatibility(url, env);
      case 'POST /api/best-config':
        return await bestConfig(request, env);
      default:
        if (url.pathname.startsWith('/admin/')) return await admin(request, url, env);
        return error(404, 'Not found');
    }
  } catch (cause) {
    if (cause instanceof BadRequest) return error(400, cause.message);
    console.error(cause);
    return error(500, 'Server error');
  }
}

export default {
  fetch: (request, env) => handle(request, env),
  // Hourly cron (wrangler.toml): drop rate-limit counters whose hour is over.
  scheduled: async (_event, env) => {
    await env.DB.prepare('DELETE FROM rate WHERE expires_at < ?1').bind(Date.now()).run();
  },
};
