# Rift AutoTune: architecture & UX implementation brief
Status: phase 1 local run-evidence analyzer implemented, unit tested; NO release APK installed.

## Objective
Make game optimization **fast, reversible, verifiable and independent of specific GameNative online APIs**.
Default goal is **Balanced**: prioritize starting reliably, smooth frame pacing, sustained playable FPS and
reasonable thermals. An increased peak FPS is not enough to claim an improvement.

## Existing code and reuse
- `assistant/DeviceProfiler.kt`: device, SoC, GPU, Vulkan, RAM, display, thermal status, installed versions.
- `assistant/GameContext.kt`: game settings, detected engine, Wine errors, run history and community evidence.
- `assistant/RunHistory.kt`: previous sessions' duration, average FPS, frame P99, crashes, heat, config snapshots.
- `assistant/optimize/Optimizer.kt`: AI recommendation, validated suggestion, application and rollback snapshot.
- `assistant/optimize/SuggestionCheck.kt`: allowed keys, actual component versions, safe limits.
- `assistant/optimize/OptimizerPrompt.kt`: instructions sent to user's configured provider.
- `assistant/optimize/AutoTuneEvidence.kt`: NEW local-only evidence analyzer. Never changes active settings.
- `assistant/optimize/OptimizeDialog.kt`: current Optimize entry leads to chat; proposal card can apply or restore.

IMPORTANT: the legacy community service, component download manifest, and some endpoints still reference
GameNative. Release requires a separate legality/provenance and endpoint audit. Do not use these APIs
as a required backend for the AutoTune feature.

## Component catalog (phase 2)
Build `RiftComponentCatalog` from trusted repository releases and verified manifests, not ad-hoc
LLM web guesses. Cache a small signed or pinned-hash JSON manifest on a Rift-controlled endpoint.
Entries: type, id, version, architecture (arm64ec/x86_64), container variant, Vulkan/API constraints,
URL, SHA-256, upstream license, release date, status (stable/experimental/revoked), known incompatibilities.
Only use a release after artifact checksum/signature and architecture validation. Do NOT silently
replace a working driver just because a newer one exists. Cache the manifest so offline optimization works.
Never let model-provided URLs become executable downloads.

## Baseline & candidate selection (phase 3)
- Identify current device + game + store and exact runtime component versions.
- If an existing profile launches successfully, pin it as `KnownGood`.
- Exclude incompatible architecture and unavailable downloadable files before AI sees candidates.
- Reuse comparable stable local run evidence first; otherwise use rated community data with
  strict GPU/build matching and freshness confidence.
- Balanced should propose at most 1-3 **compatible changes** per trial. Never mutate all the layers
  (driver, Box64, Wine, DXVK/VKD3D) simultaneously. Respect protected-game activation limits.
- AI explains rationale and risk but does not choose arbitrary versions or trigger file downloads itself.
- Treat low-confidence matches as **unverified**. Never claim guaranteed performance.
- Current local evidence is a heuristic: differing scenes, shader-compilation and game patches skew FPS.

## Session measurement (phase 4)
- Keep using SessionReport data; when supported, measure: avg FPS, frame-time P50/P99,
  1% lows (only if actual frame samples are available), launch-to-menu time, session length,
  exit reason, OS thermal status, battery delta, driver + game build IDs.
- Explicit states: STARTED / ENTERED_GAME / BENCHMARK_VALID / CRASHED / CANCELLED.
- No invented metric if source is absent: display `Not measured`.
- Ignore first-run shader compilation for benchmark comparisons, or label as warmup.
- Compare the SAME game scene, graphics settings, power state and resolution.
- Require multiple valid runs before marking a profile `Verified`.

## Trial + rollback state machine (phase 5)
`BASELINE -> CANDIDATE_READY -> USER_APPROVES -> SNAPSHOT -> APPLY -> TEST -> EVALUATE`.
- TEST success and comparison is credible -> `KEEP`; preserve prior `KnownGood` for user undo.
- Failed launch / crashes / thermal regression / major P99 degradation -> `REVERT` with explanation.
- Process killed, battery low or session incomplete -> `UNVERIFIED` and keep a reversible history entry.
- Record each snapshot and outcome atomically per app ID; never touch game installation or save files.
- A launchable but protected game does NOT permit aggressive automated restarts; require user interaction.

## UX: game page, Balanced
One primary entry: `Optimize`. Advanced tuning remains optional.
1. Idle screen: `Balanced` selected; show current versions, KnownGood/Unverified status,
   last valid session and what the optimizer knows. Secondary `Advanced settings` link.
2. `Analyze`: immediate local check (fast), asynchronously fetch cached catalog only when needed;
   show 3-step activity: Hardware -> Compatibility -> Evidence.
3. Result: `Suggested configuration`, exactly what changes, why, confidence level,
   compatibility warnings, and `Apply & test` (user action).
4. Trial: progress with cancellation, save snapshot before applying, explicit `Restore previous`.
5. Summary: `Before / After` FPS, frame P99, launch success, thermals, with `Not measured` if unknown.
   `Keep this` and `Restore` actions. Do not suggest numerical wins on invalid data.

## Validation tests
- Unit: version availability, no impossible cross-arch pair, conservative evidence grouping,
  ignoring short/throttled/crashed sessions, stable vs unstable comparison, rollback persistence.
- Android instrumentation: apply proposal then force-stop, recover snapshot, preserve container
  and Steam saves, test offline startup, no downloaded artifact without verified hash.
- UX: full controller D-pad + B/Select/Home navigation and focus; readable at TV distance.
- Regression: existing game launch flow unaffected; never update or uninstall during live session.

## Delivery gates
1. Phase 1: local evidence analyzer & prompt hookup + tests [DONE].
2. Phase 2: Rift-owned component catalog + independent source checks [NOT STARTED].
3. Phase 3: stable candidate generation & explicit approval [NOT STARTED].
4. Phase 4: controlled benchmark capture & comparability [NOT STARTED].
5. Phase 5: automatic regression detection, reversible outcome and screen UI [NOT STARTED].
6. APK validation on actual Astra with a safe non-DRM test game; fix oversized home Composable
   `RiftHomeScreenKt` VerifyError on freshly built APK **before any next install** [BLOCKED].
