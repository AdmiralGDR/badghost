# Changelog

Written for people who use the mod, not for people who read commits. Each release says what
changed for you, and how the build was checked before it went out.

Versions follow [Semantic Versioning](https://semver.org/). Dates are ISO-8601.

## [1.1.0] — 2026-08-17

The theme of this release is that the mod stopped being a black box. It tells you what it is
about to do, and when it cannot do something it tells you why instead of going quiet.

### Added
- **Preview before you commit.** Look at a bedrock block with the miner armed and the mechanism
  is drawn where it would be built — piston with its facing arrow, torch, support. If the block
  cannot be mined, the cell that is in the way is outlined instead, with the reason named.
- **Settings screen.** Every option is now editable in-game from the mod list, with its range
  and its explanation, instead of by hand in a TOML file.
- **A requirements checklist.** The HUD lists what the miner needs and how much of it you have,
  so "it will not start" turns into "you are two pistons short".
- **Session statistics** — blocks removed, attempts per block, average time. Held in memory,
  wiped when you leave the world, never written anywhere.
- **Undo and clear for ghost blocks** (`H` and `J`), and a ceiling on how many can exist at once.
- **Shapes instead of one block at a time.** A line, a wall, a floor, a platform to step out onto,
  or a hollow box — one press lays the whole thing out, and one undo takes the whole thing back
  rather than asking for forty presses. Cells that are already occupied are stepped over and the
  count is reported, so a shape that comes out smaller than you asked for tells you why. Pick the
  shape in the settings or with `/badghost template <shape> [size]`; `single block` still paints
  cell by cell while the key is held, exactly as before.
- **`/badghost` commands.** `stats`, `queue`, `why` (the reason the last target was abandoned,
  long after the message has scrolled away), `audit`, `undo`, `clear`, and `profile` to switch a
  whole group of settings at once — `careful`, `fast` or `debugging` — instead of twenty toggles.
  They are answered by your own client and never sent to the server, and that holds for mistyped
  ones too: `/badghost stst` gets an answer here rather than turning up in a server's log.
- **The mod checks its own features.** Slippery blocks, bouncy blocks, suppressed nausea and
  sideways placement each depend on a hook in the game's code. If one of those fails to attach —
  a game update moved it, another mod took the same spot — the setting used to stay on and do
  nothing at all. Now the mod notices, says which feature is not working when you join a world,
  and shows it in the HUD until it is fixed.

### Changed
- **Four settings that did nothing now work.** `frozenSlippery`, `bouncy`, `disableNegatives`
  and `cameraDistance` were saved to your config and read by nothing. Ghost blocks can now be
  slippery or bouncy underfoot, nausea no longer swirls the screen, blindness and darkness no
  longer collapse the fog, and the third-person camera distance is yours to set.
- **Every refusal names its cause.** "No space" became eight distinct reasons, each pointing at
  the position responsible.
- **Ghost blocks remember what they replaced**, so removing one puts back what was there rather
  than leaving a hole.

### Fixed
- **This mod could stop another mod from loading.** Two of its hooks claimed their call site
  exclusively, so a second mod asking for the same one failed to apply and lost the feature —
  reported in the field against another mod's bounce handler. Both now use hooks that compose,
  so mods touching the same code keep working side by side, whatever order they load in.
- The client could crash on the tick a ghost block disappeared.
- A typo in the ghost-block setting used to lock out undo and clear, stranding blocks you could
  no longer remove.
- Changing the ghost-block setting orphaned the blocks already placed.
- Ghost-block physics were read from the integrated server's thread in single player — a data
  race that could corrupt the registry.
- The miner reported "you need 2 pistons" at a player holding a stack of them in the off hand.
- With a screen open, item swapping silently does not work; the miner used to blame the wrong
  thing. It now says what is actually in the way.
- The settings screen showed raw translation keys instead of names.

### Verification
Automated: unit tests under a fatal-warning build, plus an in-game self-test that mines real
bedrock in a real world and reports which of its checks ran. Also exercised against a real
dedicated server over the network, since the mod must work without the server knowing about it.

The mod being invisible to the server is now measured rather than asserted. The self-test counts
every packet the client sends while it mines, and checks two things: that no packet type comes from
anywhere but the game itself, and that this mod never opens a channel of its own. A run mining four
bedrock blocks sends 232 packets across 7 ordinary vanilla types and nothing else, and the eleven
`/badghost` commands together send exactly none.

To be exact about what that does and does not show: the count begins after you have joined, so it
says nothing about the mod list exchanged while connecting, and it is not a claim about the timing
or contents of ordinary packets — only that nothing was added to them.

Manual: run by hand on Windows before release.

## [1.0.1] — 2026-08-15

### Changed
- Less work per frame in the HUD, the tool scan and the plan search.

## [1.0.0] — 2026-08-15

First public release. Bedrock miner, ghost blocks and ESP for NeoForge 1.21.1, client-side only.
