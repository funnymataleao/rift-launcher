-- Rift community database (Cloudflare D1, SQLite).
-- A "run" is one "How did the game run?" answer: the stars, the issues, the note and the
-- container config it was played with. Same shape as GameNative's /api/compatibility runs,
-- so Rift's CommunityConfigService can read either server.

CREATE TABLE IF NOT EXISTS games (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    name_key TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    manufacturer TEXT NOT NULL,
    model TEXT NOT NULL,
    model_key TEXT NOT NULL,
    gpu TEXT NOT NULL,
    gpu_key TEXT NOT NULL,
    soc TEXT NOT NULL,
    android_ver TEXT NOT NULL,
    UNIQUE (model_key, gpu, soc, android_ver)
);
CREATE INDEX IF NOT EXISTS devices_model_key ON devices (model_key);

CREATE TABLE IF NOT EXISTS runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id INTEGER NOT NULL REFERENCES games (id),
    device_id INTEGER NOT NULL REFERENCES devices (id),
    rating INTEGER NOT NULL,
    tags TEXT NOT NULL,
    notes TEXT NOT NULL,
    configs TEXT NOT NULL,
    config_hash TEXT NOT NULL,
    avg_fps REAL,
    session_length_sec INTEGER,
    app_version TEXT NOT NULL,
    game_store TEXT NOT NULL,
    store_game_id TEXT NOT NULL,
    -- SHA-256 of the app's random install id and the server salt: lets one install replace its
    -- own repeated answer, never names the person.
    install_hash TEXT NOT NULL,
    created_at TEXT NOT NULL,
    hidden INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS runs_game ON runs (game_id, hidden, rating, created_at);
CREATE INDEX IF NOT EXISTS runs_game_device ON runs (game_id, device_id, hidden);
CREATE INDEX IF NOT EXISTS runs_install ON runs (install_hash, game_id, config_hash);

-- Hourly counters for rate limiting; keys hold salted hashes only, rows expire after an hour.
CREATE TABLE IF NOT EXISTS rate (
    bucket TEXT PRIMARY KEY,
    count INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);
