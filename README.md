# BadGhost

**A client-side NeoForge mod for Minecraft 1.21.1** (Java 21). It loads only on the
client (`@Mod(dist = Dist.CLIENT)`); on a dedicated server it does nothing. It is *not*
a server optimization package, despite the current `mod_description` in `gradle.properties`.

## What it does

- **Ghost blocks** — client-only blocks placed/undone/cleared with key bindings
  (`Place ghost block`, `Take back last placement`, `Remove all ghost blocks`); templates
  and profiles via `/badghost template …`, `/badghost profile …`.
- **Bedrock miner automation** — plans and runs the bedrock-breaking glitch
  (`automation/`: `PlanFinder`, `MiningPlan`, `MinerTask`), with a preview of the plan,
  optional auto-scan and an ESP highlight (`visuals/EspRenderer`). Arm/disarm with a key.
- **Status HUD** (`MinerHUD`), toggleable by key or config; hides with F1.
- **Client tweaks via mixins** — slippery/bouncy block physics (`BlockFrictionMixin`,
  `EntityBounceMixin`), nausea swirl suppression (`ConfusionOverlayMixin`), negative-effect
  filter, plus `ServerboundMovePlayerPacketMixin` and `ClientSendMixin` (counts every
  outgoing packet so `/badghost stats` can show what the client sends).
- **Client commands** — `/badghost help | stats | why | undo | clear | profile | template`,
  answered locally, never forwarded to the server.
- English and Russian translations; in-game config screen built from the config spec
  (`ghostBlock`, `automationEnabled`, `previewEnabled`, `autoScanEnabled`, `espEnabled`,
  `hudEnabled`, `disableNegatives`, …).

> Note: some features (the bedrock glitch, physics/effect changes) give a client-side
> advantage and may violate the rules of multiplayer servers. Use on servers only where allowed.

## Build

Requires JDK 21.

```sh
scripts/build.sh      # ./gradlew build, writes build/libs/*.jar + .sha256 (reproducible)
scripts/test.sh       # unit tests (-Werror) + translation key coverage
./gradlew runClient   # dev client
```

In-game checks: `scripts/smoke-client.sh` (startup), `scripts/selftest.sh` (real world,
needs a cheats-enabled singleplayer save), `scripts/selftest-server.sh` (same over a local
dedicated server), `scripts/verdict.sh`.

Install: drop the jar into the client's `mods/` folder (NeoForge 21.1.x).

## Layout

```
src/main/java/red/team/badghost/
  Badghost.java    entry point (client-only)
  automation/      miner engine, plan finder, preview
  command/         client commands
  config/          config spec, profiles
  core/            client context, feature audit, packet log, stats
  dev/             in-game self-test
  mixin/           Mixin hooks
  utils/, visuals/ helpers, HUD, ESP, ghost-block registry, keybinds
src/test/          JUnit tests
```

## License

Source headers and `mod_license` declare **GNU AGPL-3.0** (© RedTeam). See `LICENSE`.

## Proposed GitHub description

Current: "Server-side optimization package to improve tick handling." — inaccurate.

Proposed: **"Client-side NeoForge 1.21.1 mod: ghost blocks, bedrock-miner automation, HUD and ESP. Installed on the client only, but it changes what the client sends to the server."**

(`mod_description` in `gradle.properties` should be changed to match.)
