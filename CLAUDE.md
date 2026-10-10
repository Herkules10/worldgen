# Fantasy Worldgen
NeoForge mod (Minecraft 1.21.1, Java 21, ModDevGradle, Mojang names + Parchment)
with its own terrain generation engine for epic fantasy landscapes.

## Layout
- engine/  pure Java terrain engine. NEVER import net.minecraft or net.neoforged here.
- preview/ CLI that renders engine output to PNGs in build/previews/
- mod/     thin NeoForge adapter: ChunkGenerator, registration, GameTests

## Commands (use these exact commands; all must pass before you say you're done)
- Engine tests:   ./gradlew :engine:test
- Preview render: ./gradlew :preview:run --args="--seed 42 --size 2048"
- In-game tests:  ./gradlew :mod:runGameTestServer
- Full build:     ./gradlew build
- World render:   ./gradlew runRender -Pseed=42 -Px=0 -Pz=0 -Psize=512
  Real in-game generation (headless server, fresh world every run) →
  build/renders/<name>_{map,height,biome,slice}.png + <name>.json (heights, biome
  shares, structures). Optional: -Pscale=N (blocks/pixel), -PsliceZ, -Pdimension,
  -PlevelType, -Pname. Exit code 1 on failure. Read the JSON, then LOOK at the PNGs.
  Identical args → pixel-identical output; compare before/after only with identical args
  (a different region framing can shift features that cross chunk borders).

## Rules
- This is NeoForge 1.21.1, not old Forge. Verify APIs against the decompiled
  sources and https://docs.neoforged.net/docs/1.21.1/ before using them.
- Every terrain change: determinism test + a preview render you have LOOKED at.
- Same seed + coordinates must always give identical output. No unseeded Random.
- Never run runClient yourself; it needs a human. Ask me to check in-game.

## mod/ gotchas
- runGameTestServer crashes if no GameTests are registered.
- `runData` writes to the mod's `src/generated/resources/` (on the resource path);
  commit its output except `.cache`.
- `neoforge.mods.toml` lives in `src/main/templates/` and is expanded from
  `gradle.properties` by `generateModMetadata`. Edit the template; a `${prop}`
  missing from `replaceProperties` in the build script fails the build.
- Gradle configuration cache is on: build-script changes must be compatible.
- Client-only code goes in a `@Mod(value = MODID, dist = Dist.CLIENT)` class.
- Package root is `com.herkules10.fantasyworldgen` (`.engine`, `.preview`, mod at the
  root). Assets and data use the `fantasyworldgen` namespace.
- The engine is not a mod: `mod/` adds its source set to the mod (`neoForge.mods`) and
  packs its classes into the mod jar. Keep `compileOnly project(':engine')`; a runtime
  dependency would load the engine's packages twice and crash.
- World height is engine-owned (`WorldShape`: y -64..2031, the max Minecraft allows;
  the user plays with Distant Horizons). `dimension_type/overworld.json` must match;
  a GameTest checks it.
- Dev-client mods (Distant Horizons) go in the `clientMods` configuration; `runClient`
  syncs them into `mod/run/mods` as `dev-*.jar`. Never add them to `localRuntime`, which
  would load them in GameTests and renders too. GameTests run in `mod/run-gametest/`.
- Every engine `BiomeType` needs `data/fantasyworldgen/worldgen/biome/<id>.json` and a
  `biome.fantasyworldgen.<id>` lang key; a GameTest checks registration.

## engine/ gotchas
- Transcendental math only via `Maths` (StrictMath): `Math.sin/pow/hypot` may differ
  between JVMs and break determinism. Caches must only hold pure-function results.
- Placement rules from Artistic_Direction.md are tests (`BiomePlacementTest`); a tuning
  change that breaks the story fails them. Look at `_biome.png` / `_fields.png`.
