// Pure helpers: validation, normalisation and config matching. The rules mirror GameNative's
// client (app/gamenative/api/CommunityConfigService.kt), so a config read back from this server
// passes the same checks as one from api.gamenative.app.

export const LIMITS = {
  bodyBytes: 64 * 1024,
  nameChars: 256,
  notesChars: 4 * 1024,
  configValueChars: 64 * 1024,
  launchArgumentChars: 4 * 1024,
  tags: 20,
  pageSize: 200,
  runsPerInstallPerHour: 30,
  runsPerAddressPerHour: 60,
  // An install answering again for the same game and config within this window replaces its answer.
  repeatWindowMs: 10 * 60 * 1000,
};

// The choices of GameNative's GameFeedbackDialogState.AVAILABLE_TAGS.
export const ALLOWED_TAGS = new Set([
  'controller_issues',
  'no_graphics',
  'cloud_saves_missing',
  'graphics_glitches',
  'audio_glitches',
  'does_not_open',
  'directx_error',
]);

// A config with one of these did not really work, so it is never offered as the best one.
export const FAILURE_TAGS = new Set(['no_graphics', 'does_not_open', 'directx_error']);

// communityConfigAllowedKeys in CommunityConfigService.kt.
export const CONFIG_KEYS = new Set([
  'graphicsDriver',
  'graphicsDriverVersion',
  'graphicsDriverConfig',
  'dxwrapper',
  'dxwrapperConfig',
  'startupSelection',
  'box64Version',
  'box64Preset',
  'containerVariant',
  'wineVersion',
  'emulator',
  'fexcoreVersion',
  'fexcoreTSOMode',
  'fexcoreX87Mode',
  'fexcoreMultiBlock',
  'fexcorePreset',
  'useLegacyDRM',
  'launchRealSteam',
  'launchBionicSteam',
  'steamOfflineMode',
  'epicOfflineMode',
  'unpackFiles',
  'suspendPolicy',
  'executablePath',
  'cpuList',
  'cpuListWoW64',
  'audioDriver',
  'wincomponents',
  'videoMemorySize',
  'execArgs',
  'envVars',
]);

export class BadRequest extends Error {}

export function cleanString(value, maxChars = LIMITS.nameChars) {
  if (typeof value !== 'string') return '';
  // eslint-disable-next-line no-control-regex
  return value.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, maxChars);
}

export function nameKey(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ');
}

export function modelKey(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, '');
}

// communityDeviceQuery: "Manufacturer Model" unless the model already starts with the maker.
export function deviceName(manufacturer, model) {
  const maker = manufacturer.trim();
  const name = model.trim();
  if (!maker) return name;
  if (name.toLowerCase().startsWith(maker.toLowerCase())) return name;
  return `${maker} ${name}`.trim();
}

// canonicalCommunityGpu.
export function gpuKey(value) {
  const cleaned = String(value).toLowerCase().replaceAll('(tm)', ' ').replace(/\s+/g, ' ').trim();
  if (!cleaned || cleaned.includes('unknown') || cleaned === 'n/a') return '';
  let match = cleaned.match(/\badreno[ -]*([a-z]?\d+)\b/);
  if (match) return `adreno:${match[1]}`;
  match = cleaned.match(/\b(mali|immortalis)[ -]*([a-z]\d+)\b/);
  if (match) return `arm:${match[2]}`;
  match = cleaned.match(/\bxclipse[ -]*(\d+)\b/);
  if (match) return `xclipse:${match[1]}`;
  return cleaned.replace(/[^a-z0-9]+/g, '');
}

// communityGpuCompatibility: which configs can be shared between different GPUs.
export function gpuFamily(value) {
  const key = gpuKey(value);
  if (!key) return 'unknown';
  if (/^adreno:[67][0-9]{2}$/.test(key)) return 'adreno_standard';
  if (key === 'adreno:a12' || /^adreno:8[3-5][0-9]$/.test(key)) return 'adreno_elite';
  return 'other';
}

// normalizeCommunityGameStore.
export function gameStore(value) {
  const normalized = String(value ?? '').toLowerCase().replace(/[^a-z0-9]+/g, '');
  switch (normalized) {
    case 'steam':
      return 'steam';
    case 'epic':
    case 'epicgames':
    case 'epicgamesstore':
      return 'epic';
    case 'gog':
    case 'gogcom':
      return 'gog';
    case 'amazon':
    case 'amazongames':
      return 'amazon';
    case 'custom':
    case 'customgame':
      return 'custom';
    default:
      return cleanString(value);
  }
}

export function sanitizeConfig(raw) {
  let value = raw;
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value);
    } catch {
      throw new BadRequest('configs is not JSON');
    }
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new BadRequest('configs must be an object');
  const config = {};
  for (const [key, entry] of Object.entries(value)) {
    if (!CONFIG_KEYS.has(key)) continue;
    if (entry === null || typeof entry === 'object') continue;
    if (typeof entry === 'string') {
      const limit = key === 'execArgs' ? LIMITS.launchArgumentChars : LIMITS.configValueChars;
      if (entry.length > limit || entry.includes('\u0000')) continue;
    }
    if (typeof entry === 'number' && !Number.isFinite(entry)) continue;
    config[key] = entry;
  }
  if (Object.keys(config).length === 0) throw new BadRequest('configs has no known settings');
  return config;
}

function optionalNumber(value, { min, max, integer }) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  if (!Number.isFinite(number) || number < min || number > max) return null;
  return integer ? Math.round(number) : number;
}

/** Checks a POST /api/game-run body (GameRunApi's fields plus Rift's installId, gameStore, storeGameId). */
export function parseGameRun(body) {
  if (!body || typeof body !== 'object') throw new BadRequest('body must be a JSON object');
  const gameName = cleanString(body.gameName);
  if (!gameName || !nameKey(gameName)) throw new BadRequest('gameName is required');
  const rating = Number(body.rating);
  if (!Number.isInteger(rating) || rating < 1 || rating > 5) throw new BadRequest('rating must be 1 to 5');
  const installId = cleanString(body.installId, 128);
  if (installId.length < 16) throw new BadRequest('installId is required');
  const model = cleanString(body.deviceModel);
  if (!model) throw new BadRequest('deviceModel is required');
  const tags = Array.isArray(body.tags)
    ? [...new Set(body.tags.filter((tag) => typeof tag === 'string' && ALLOWED_TAGS.has(tag)))].slice(0, LIMITS.tags)
    : [];
  const rawConfig = typeof body.configs === 'string' ? safeJson(body.configs) : body.configs;
  const config = sanitizeConfig(rawConfig ?? body.configs);
  return {
    gameName,
    gameStore: gameStore(body.gameStore) || inferStore(rawConfig ?? {}),
    storeGameId: cleanString(String(body.storeGameId ?? ''), 64),
    manufacturer: cleanString(body.deviceManufacturer),
    model,
    gpu: cleanString(body.gpuName),
    soc: cleanString(body.socName ?? ''),
    androidVersion: cleanString(body.androidVersion, 32),
    appVersion: cleanString(body.appVersion, 64),
    config,
    rating,
    tags,
    notes: cleanString(body.notes ?? '', LIMITS.notesChars),
    avgFps: optionalNumber(body.avgFps, { min: 0, max: 1000, integer: false }),
    sessionLengthSec: optionalNumber(body.sessionLengthSec, { min: 0, max: 7 * 24 * 3600, integer: true }),
    installId,
  };
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// inferCommunityGameStore: the container id starts with the store.
function inferStore(config) {
  const id = String(config.id ?? '').toUpperCase();
  if (id.startsWith('STEAM_')) return 'steam';
  if (id.startsWith('EPIC_')) return 'epic';
  if (id.startsWith('GOG_')) return 'gog';
  if (id.startsWith('AMAZON_')) return 'amazon';
  return '';
}

/** A stable fingerprint of a config, so the same setup from one install is counted once. */
export function configFingerprint(config) {
  const sorted = Object.keys(config).sort().map((key) => [key, config[key]]);
  return JSON.stringify(sorted);
}

/**
 * Picks the best config for a GPU from candidate runs (each with device_gpu, rating, tags, avg_fps,
 * created_at, game_store). Same match types as GameNative's /api/best-config.
 */
export function pickBestConfig(runs, gpuName, store) {
  const usable = runs.filter((run) => run.rating >= 4 && !run.tags.some((tag) => FAILURE_TAGS.has(tag)));
  const currentKey = gpuKey(gpuName);
  const currentFamily = gpuFamily(gpuName);
  const tiers = [
    ['exact_gpu_match', (run) => currentKey !== '' && gpuKey(run.device_gpu) === currentKey],
    [
      'gpu_family_match',
      (run) =>
        (currentFamily === 'adreno_standard' || currentFamily === 'adreno_elite') &&
        gpuFamily(run.device_gpu) === currentFamily,
    ],
    ['fallback_match', () => true],
  ];
  const wantedStore = gameStore(store);
  const order = (a, b) =>
    Number(b.game_store === wantedStore) - Number(a.game_store === wantedStore) ||
    b.rating - a.rating ||
    (b.avg_fps ?? -1) - (a.avg_fps ?? -1) ||
    String(b.created_at).localeCompare(String(a.created_at));
  for (const [matchType, matches] of tiers) {
    const candidates = usable.filter(matches).sort(order);
    if (candidates.length > 0) return { matchType, run: candidates[0] };
  }
  return { matchType: 'no_match', run: null };
}

export async function sha256Hex(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
