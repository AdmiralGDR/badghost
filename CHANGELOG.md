# Changelog

Written for people who use the mod, not for people who read commits. Each release says what
changed for you, and how the build was checked before it went out.

Versions follow [Semantic Versioning](https://semver.org/). Dates are ISO-8601.

## [1.2.0] — 2026-08-17

Mostly words this time. Every setting now explains itself in your own language, and the panel can
be got out of the way.

### Added
- **The status panel can be turned off** — in the settings, or with a key so you can do it without
  leaving the game. The miner carries on working either way, and `/badghost stats` and
  `/badghost why` still answer. The panel also disappears with the rest of the interface when you
  press F1, which it should always have done.

### Changed
- **Every setting explains itself, in your language.** The settings screen used to show its
  explanations in English whatever language you played in, because it fell back to the raw comment
  in the config file. All thirty-two settings and sections now have a real translated description,
  and the descriptions say what a setting does not do as well as what it does.
- **Plainer wording throughout.** Labels and messages were written half in mod jargon: the settings
  said "ghost blocks" where the messages said something else, key names shouted in title case, and
  the Russian text left words like "Bedrock Miner" and "ESP" untranslated or transliterated. Both
  languages now use the same plain words for the same things.

### Fixed
- Shifting player models could corrupt everything drawn afterwards if the setting changed at the
  wrong instant, because the two halves of the drawing code each decided for themselves whether a
  shift was in effect.

### Verification
Automated: 98 unit tests under a fatal-warning build, and twelve in-game checks that run in a real
world — mining real bedrock, then confirming the panel switch, the shapes, the commands and the
packet count. All twelve also pass against a dedicated server over the network. Two clean builds
produce byte-identical jars.

The check on the settings texts is worth naming, because it is the one that would have let this
release ship half-translated: it reads the list of settings out of the mod's own configuration
rather than a list kept by hand, so a setting added without a description fails the build instead
of reaching you as an English sentence under a translated label. It was tested by breaking it three
different ways on purpose and confirming it noticed each one.

Manual: run by hand on Windows before release.

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
bedrock blocks sends a couple of hundred packets across seven or eight ordinary vanilla types —
how many depends on how long the run takes and whether it is over a network, so the count is not a
fixed number — and nothing outside that. The eleven `/badghost` commands together send exactly
none, which is a fixed number.

To be exact about what that does and does not show: the count begins after you have joined, so it
says nothing about the mod list exchanged while connecting, and it is not a claim about the timing
or contents of ordinary packets — only that nothing was added to them.

Manual: run by hand on Windows before release.

## [1.0.1] — 2026-08-15

### Changed
- Less work per frame in the HUD, the tool scan and the plan search.

## [1.0.0] — 2026-08-15

First public release. Bedrock miner, ghost blocks and ESP for NeoForge 1.21.1, client-side only.
