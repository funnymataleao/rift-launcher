# Rift community server

Rift's own database of "How did the game run?" answers and the configs they were played with.
GameNative's API at api.gamenative.app serves official GameNative builds only, so Rift keeps its
answers here instead.

It is a Cloudflare Worker with a D1 (SQLite) database. Routes and JSON follow api.gamenative.app,
so the app's `CommunityConfigService(baseUrl = …)`, `BestConfigService` and `GameRunApi` parsers
read it unchanged:

| Route | What |
|---|---|
| `POST /api/game-run` | one answer: GameRunApi's body plus `installId`, `gameStore`, `storeGameId` |
| `GET /api/games/search?q=` | `{games: [{id, name}]}` |
| `GET /api/devices?model=` | `{devices: [{id, model, gpu, androidVer, soc}]}` |
| `GET /api/compatibility?gameId=&sort=rating\|created_at&dir=&page=&limit=&deviceId=\|gpu=` | `{runs, page, pageSize, total}` |
| `POST /api/best-config` | `{bestConfig, matchType, matchedGpu, matchedDeviceId, matchedStore}`; match types `exact_gpu_match`, `gpu_family_match`, `fallback_match`, `no_match` |
| `GET /admin/runs`, `POST /admin/runs/:id/hide\|unhide`, `DELETE /admin/runs/:id` | moderation, `Authorization: Bearer <ADMIN_TOKEN>` |

What is stored: game, device model and maker, GPU, SoC, Android version, Rift version, the
container settings a community config may carry (same allow-list as the app, no container id,
drives or paths), stars, issue tags, the note, average FPS and session length. No names, accounts
or IP addresses: the install id and the address are only kept as salted SHA-256 hashes, the
address only in hourly rate-limit counters. Limits: 30 answers an hour per install, 60 per address;
the same install answering again for the same game and setup within 10 minutes replaces its answer.
Best config skips runs under 4 stars and runs marked "does not open", "no graphics" or "DirectX error".

## Tests

```
npm test
```

Node 22 or newer (uses the built-in `node:sqlite` as a stand-in for D1).

## Deploy

1. `npx wrangler@4 login` (opens the browser; sign in or sign up at Cloudflare, free plan).
2. `npx wrangler@4 d1 create rift-community` and put the printed `database_id` into `wrangler.toml`.
3. `npx wrangler@4 d1 execute rift-community --remote --file schema.sql`
4. Secrets, random values:
   ```
   openssl rand -hex 32 | npx wrangler@4 secret put HASH_SALT
   openssl rand -hex 32 | tee admin-token.txt | npx wrangler@4 secret put ADMIN_TOKEN
   ```
   Keep `admin-token.txt` private (it is in `.gitignore`), it opens the moderation routes.
5. `npx wrangler@4 deploy` prints the address, `https://rift-community.<account>.workers.dev`.
6. Put that address into `RiftCommunity.BASE_URL`
   (`app/src/main/java/dev/rift/launcher/community/RiftCommunity.kt`). Until then Rift keeps the
   answers only on the tablet, in `<container>/.rift/feedback/`.

Schema changes: edit `schema.sql` (keep it `IF NOT EXISTS`) and run step 3 again.
