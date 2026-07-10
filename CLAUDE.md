# zChat — Developer Context

Lightweight, group-based **chat formatting and moderation** plugin for **Paper** (and
Folia) networks. It handles the chat essentials almost every server wants — per-group
message formatting, a word/pattern filter, a per-player chat cooldown, a global chat
mute, chat clearing, and a per-player chat toggle — each individually toggleable,
permissioned, and with fully customizable MiniMessage text.

Unlike a command-only plugin, zChat has a genuine **hot path**: it runs on every chat
message (asynchronously on Paper, on a region thread on Folia), so that path must be
allocation-light and lock-free. Priority order: **stability > performance / lightweight
> configurability > code quality**. It never throws out of enable/reload, never
self-disables on a bad config value, never blocks the main thread, and is thread-correct
on both Paper and Folia.

This is **v2** — a from-scratch rewrite of the archived
[zChat v1](https://modrinth.com/plugin/zchat) (Spigot, Java 8, LuckPerms-required),
now **GPLv3-only**, faster, lighter, and dependency-free (permission-node groups instead
of a hard LuckPerms dependency).

## Conventions (IMPORTANT)
- **Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`, `ci:`, `refactor:`,
  `test:`, `build:`), scoped where useful. Commit in small, feature-grouped increments.
- **Do NOT** add a `Co-Authored-By: Claude` trailer to commits.

## Build / target
- **Paper API `1.20.6-R0.1-SNAPSHOT`**, **Java 21** bytecode. 1.20.6 is the first release
  where the modern Brigadier command API (`io.papermc.paper.command.brigadier` —
  `Commands`, `CommandSourceStack`, `LifecycleEvents.COMMANDS`) is **stable /
  non-experimental**, and it bundles Adventure + MiniMessage + the modern
  `AsyncChatEvent`/`ChatRenderer` API. The version target has **no runtime perf impact**
  — it only gates which APIs compile, and a 1.20.6-built jar runs unchanged on all newer
  Paper. Going below 1.20.6 would cost the stable Brigadier API and the viewer-unaware
  chat renderer.
- **Folia-supported.** `folia-supported: true` in `paper-plugin.yml`; all runtime state
  is concurrent and every player mutation runs on the correct scheduler
  (`util/Schedulers`).
- Gradle Kotlin DSL + `com.gradleup.shadow`. Build: `./gradlew build` → shaded runnable
  jar in `build/libs/zChat-<version>.jar`.
- **Semantic versioning, git-tag driven.** A commit tagged `vX.Y.Z` builds to a clean
  version (e.g. `2.0.0`); any commit after the latest tag builds
  `<version>-nightly.<n>+<sha>`; with no tags it falls back to `0.0.0-nightly.<count>+<sha>`.
  Full SHA + timestamp are injected into `build-info.properties` and the version into
  `paper-plugin.yml` via `processResources` `expand`.

## Layout (`co.xenastudios.zchat`)
- `ZChatPlugin` — fail-safe enable/disable, registers the `LifecycleEvents.COMMANDS`
  handler, holds the atomic `volatile Settings` snapshot and the long-lived `ChatState`,
  registers the single chat listener.
- `config/` — `Settings` (immutable snapshot: formatting groups, filter patterns
  pre-compiled, cooldown, and per-command message blocks), `Msg` (pre-parsed MiniMessage
  + raw template for placeholder cases), `ConfigLoader` (validate + migrate + version;
  never throws).
- `chat/` — `ChatListener` (the one always-on listener: the whole async pipeline),
  `ChatState` (thread-safe runtime state: global mute flag, per-player "chat hidden" set,
  cooldown timestamps — kept OUT of the settings snapshot so reload never clears it),
  `Filters` (pure, unit-testable match/censor helpers).
- `command/` — one class per command building its Brigadier node, `Cmd` (shared sender /
  permission / player-only helpers), and `CommandRegistrar` (registers only enabled
  commands, each guarded).
- `util/` — `Text`, `Schedulers` (Folia detection + entity/global scheduling),
  `BuildInfo`.

## Key design rules
- **Hot-path discipline.** `ChatListener.onChat` reads only the immutable `Settings`
  snapshot and the concurrent `ChatState`. No `getConfig()`, no static-text MiniMessage
  parse, no regex compile on the chat path — formats are stored as raw templates parsed
  once per message (they carry the `<message>` placeholder), and filter patterns are
  pre-compiled at load. Gates run cheapest-first: mute → cooldown → filter → format →
  viewer hiding.
- **Formatting is the only place player text is inserted**, and it goes in as a
  `Component` (never re-parsed), so players can't inject MiniMessage — unless they hold
  the configurable `formatting.color-permission`, which opts them into colouring their
  own message via a **restricted** MiniMessage (`chat/PlayerColors`: colour/decoration
  tags only — never `<click>`/`<hover>`/`<insert>`, so no clickable-exploit vector).
- **PlaceholderAPI is an optional soft-depend** (`util/Placeholders`, guarded by a
  one-time class check) expanded in the format template only — never in the player's
  message — so a player can't smuggle a `%placeholder%` into chat. Absent PAPI = no-op.
- **Group resolution is O(groups).** Groups are sorted highest-`weight`-first at load;
  the listener returns the first group whose permission the sender holds (or the open
  `default` group / `default-format`). No per-player caching — a permission lookup is
  cheap and always current.
- **Viewer-unaware renderer.** The `ChatRenderer` is rendered once per message, not once
  per viewer.
- **Runtime state survives reload.** Mute/toggle/cooldown live in `ChatState`, not
  `Settings`. `/zchat reload` swaps the settings snapshot only. Player state is dropped on
  quit (`PlayerQuitEvent`) so nothing leaks.
- **Register only what's enabled.** Command nodes are built only for enabled commands.
- **Fail-safe.** `onEnable`/`reload` and each command registration are wrapped; a bad
  value or one broken command is logged and skipped, never taking the server down. A
  malformed format template falls back to a plain `name: message` rendering rather than
  dropping the message.

## Reload semantics
- `/zchat reload` swaps in a freshly parsed/validated snapshot; **all message + behaviour**
  values (group formats, filter patterns/mode, cooldown seconds, all text) apply live.
- **Structural** config — which commands are enabled and their aliases — is applied at
  registration (startup) and is **restart-only**.
- **Runtime state** (global mute, per-player chat-hidden, cooldown timers) is preserved
  across a reload.

## Permissions
Declared in `paper-plugin.yml` with safe defaults: moderation commands (`clearchat`,
`mutechat`) and every `zchat.bypass.*` node → `op`; the self-scoped `togglechat` →
everyone; per-group format nodes (`zchat.group.vip/staff/admin`) → `op` (opt-in), while
the ungated `default` group applies to everyone. Bypasses: `zchat.bypass.mute`,
`zchat.bypass.cooldown`, `zchat.bypass.filter`, `zchat.bypass.clearchat`.

## Status
Full plugin skeleton, server-free unit tests (`Filters` censor/match, `ChatState`
cooldown/toggle/mute, `PlayerColors` tag-restriction, `ConfigLoader` validation), CI
(`build.yml` + `nightly.yml` + `release.yml`), README, and a
fully-commented `config.yml`. The rolling `nightly` pre-release carries a stable
`zChat-nightly.jar` built from `main`; semantic releases are cut by pushing a `vX.Y.Z`
tag, which also publishes to Modrinth.

## Decisions log
- **No LuckPerms dependency.** v1 required LuckPerms; v2 resolves format groups purely by
  permission node (`zchat.group.<name>`) so it runs standalone. LuckPerms (or any perms
  plugin) still drives who holds those nodes.
- **Single fallback format.** `formatting.default-format` is the one no-match fallback; an
  open (permission-less) group is still supported if an operator adds one, but none ships
  — this removed the earlier redundant open `default` group.
- **Colour permission is tag-restricted**, PAPI is expanded on the template only, and the
  cooldown is only marked once a message survives the filter — a blocked message never
  burns a player's cooldown. Runtime `ChatState` splits the read-only `remaining` check
  from `markSent` for this reason.
- Group formats are **data-driven** (config-map key = group name), each with a `weight`
  (tie-break / priority), a gate `permission` (blank = open), and a MiniMessage `format`.
- Runtime state is deliberately **in-memory only** (mute/toggle/cooldown) to stay
  lightweight; it resets on restart, which is the expected behaviour for these features.
- Two release tracks (mirroring xUtilities / xLimbo): the `nightly` pre-release (stable
  asset name `zChat-nightly.jar`) and tag-driven semantic releases that also publish to
  Modrinth (gated on `MODRINTH_TOKEN` secret + `MODRINTH_PROJECT_ID` variable; skips
  cleanly if unset). Default Modrinth loaders: `paper,folia,purpur`.
- `.idea/` is untracked and gitignored.

## Follow-ups (not required by the brief)
- Per-world / per-channel formatting, private messaging, and join/leave message control
  are candidate future features, not in the current scope.
- The filter matches literal text / regex only — no normalization, so leetspeak and
  spacing bypass it (and substring patterns hit the Scunthorpe problem). Documented; a
  normalization pass is a candidate improvement.
- Config-validation is covered via the `ConfigLoader.buildFrom` seam (enum/clamp/regex/
  sort); the full `load(Plugin)` disk path would still need MockBukkit end-to-end.
- **Not yet smoke-tested on a live 1.20.6 / latest Paper / Folia server** before
  production use — the outstanding pre-production gap.

Remote: `git@github.com:xena-studios/zchat.git`.
