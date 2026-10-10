# Artistic Direction

## Aim

Fantasy Worldgen turns Minecraft into a world that feels **old, vast and mythic**, the kind of
landscape you'd find on the endpaper map of an epic fantasy novel. Every region should have a
story you can read from the terrain alone: what shaped it, what lived there, what went wrong.
Each biome should be memorable enough that players name places after it ("meet me at the
Scar"), and every one should give the player at least one view worth stopping for.

Guiding principles:
- **Landform first, decoration second.** A biome is mainly a *shape* of land. Blocks and
  vegetation reinforce the shape and never stand in for it.
- **Bombastic, but earned.** We're after big vistas: things hanging in the sky, things
  that are far too large, views that stretch to the horizon. Between them, the land has to
  be calm enough for the big moments to land.
- **Every biome has a history.** Its terrain, blocks and structures should all point to the
  same backstory.
- **Readable at a distance.** Silhouettes, color bands and skylines should tell you where
  you are before you get there.
- **Families, not instances.** No two regions of the same biome should look the same.
  See [Variation](#variation-every-biome-is-a-family).

## Approach: one engine for terrain and biomes

Most world generation mods stitch two separate systems together: a terrain generator that
builds a heightmap without knowing what biome sits on it, and biome mods that repaint the
surface afterward. That's where the familiar problems come from: deserts painted onto
mountainsides, hard seams where two biomes meet, and features that ignore the land they sit
on.

This mod owns **both** layers in a single engine (`engine/`), so they can inform each other.

It does **not** build on Minecraft's default generation. There's no vanilla noise router,
no vanilla density functions, no biome source or climate parameters, and no surface rules.
The engine is a standalone, pure-Java terrain generator with its own model of how land is
formed. Minecraft only receives the finished blocks. Wherever this document talks about a
biome "shaping" terrain, it means the engine's own primitives, not vanilla's. The terms
below describe artistic intent, not a specific technique.

- **Biomes shape terrain.** Each biome brings its own landform logic (spires, dunes, scale
  ridges, craters) instead of just a surface palette. How that logic is expressed is the
  engine's choice, whether as heightfields, 3D shape fields, sculpted or stamped features,
  erosion passes, or something else.
- **Terrain shapes biomes.** Biome placement reads elevation, slope, erosion and
  "history" fields, so a desert forms in a mountain's rain shadow and doesn't appear at
  random.
- **Blended transitions.** Neighbouring biomes blend their *landforms*, so mountains
  erode into foothills and dragonscale plates sink gradually under dunes. There are no
  seams.
- **Shared world fields.** Global fields (tectonic stress, heat, moisture, corruption,
  ancient magic) feed every biome, so neighbouring regions agree on the same story.
- **Structures are part of the terrain.** Structures are planned along with the land:
  roads follow passes, ruins sit in the craters that wrecked them, and terrain gets carved
  to fit a structure so nothing ends up floating on a slope.

The `mod/` layer is a thin adapter. It hands the engine's output to Minecraft through a
custom `ChunkGenerator` and replaces vanilla's pipeline instead of plugging into it. All the
creative decisions live in the engine.

## Variation: every biome is a family

A world needs far more variety than nine fixed biomes can give it. So each biome in this
document is an **archetype**, a family that the engine can generate in many distinct
**variants**. When the engine places a region of a biome, it also rolls that region's
**variant traits** from the seed and the region's position. Those traits decide its
palette, its shapes and the specifics of its story.

Every biome describes its variation with the same axes:

| Axis | Question it answers | Example (Dragonscale) |
|---|---|---|
| **Palette** | Which colors and block families? | Obsidian-and-amethyst vs. sandstone-and-gold |
| **Form** | Which shapes, proportions and silhouettes? | Serpent coiled in a ring vs. winged beast sprawled flat |
| **Scale** | How big, how dense, how extreme? | A wyrmling the size of a hill vs. a leviathan the size of a range |
| **State** | How old, how worn, what happened since? | Freshly fallen vs. fossilized vs. overgrown by forest |
| **Context** | How does it react to neighbours and world fields? | A frost-scaled creature beside the Spine, a sun-bleached one in the Glass Waste |

Rules for variants:
- **The family must stay recognizable.** However different a variant is, a player should
  still know immediately "this is a Dragonscale." The core landform signature stays fixed,
  and everything else can change.
- **Palettes are curated, not random.** Each family has a hand-designed list of palettes
  (around 5–12), each a coherent set of base, accent and highlight blocks. The engine picks
  from and blends between them, but never throws random blocks together.
- **Traits are deterministic.** Same seed and region always give the same variant. All
  randomness comes from seeded sources.
- **Traits come from context first.** Where possible, a trait follows from the world: cold
  regions get icy variants, regions near the Scar get corrupted ones. Pure chance fills in
  the rest.
- **Rarity tiers.** Most variants are *common*. A few are *rare* (one in tens of regions),
  and a handful are *legendary* (maybe one per world). Legendary variants are the stories
  players tell each other.
- **Every variant gets a name.** The engine generates a place name from its traits ("The
  Ashen Coil", "Verdigris Hide"). It can show up on maps, signs and books, and it makes
  each place feel like it has history.

---

## Biomes

### 1. Mountains: *The Spine of the World*

Mountains that are actually huge. Not stretched noise, but ranges with a geology.

- **Landform:** Long ranges with a clear main ridge, side ridges that branch like ribs, and
  deep glacial valleys between them. Peaks are sharp and asymmetric: a sheer face on one
  side, a long shoulder on the other. A few peaks rise far above the rest as landmarks you
  can see from hundreds of blocks away.
- **Layers by altitude:**
  - *Foothills:* rolling meadows, scattered boulders (glacial erratics), birch and spruce.
  - *Treeline:* stunted, wind-bent spruce that thins into scree slopes and alpine flowers.
  - *Rock band:* exposed stone in visible strata (stone, andesite, tuff, calcite), tilted
    at the same angle across the whole range.
  - *Summit:* packed and blue ice, snow cornices hanging over cliff edges.
- **Signature features:**
  - **Hanging valleys** whose waterfalls drop straight into the main valley.
  - **Cirque lakes:** bowl-shaped tarns carved under the peaks, frozen at high altitude.
  - **Knife-edge arêtes:** one-block-wide ridges that are risky but walkable.
- **Variation:**
  - *Palette:* grey granite (stone, andesite), red-rock ranges (granite, terracotta,
    red sandstone), black volcanic ranges (basalt, blackstone, tuff), and rare white
    marble ranges (calcite, diorite, quartz).
  - *Form:* jagged horn peaks, flat-topped table mountains, forests of needle spires,
    or a single huge massif.
  - *State:* heavily glaciated vs. dry and bare; young and sharp vs. old and rounded.
  - *Legendary:* a peak so tall it pokes above the cloud layer.
- **Mood:** awe and solitude. The Spine is old and doesn't care about you.

### 2. Desert: *The Glass Waste*

A desert left behind by a cataclysm. Long ago, a sky-fire melted the land, and wind has been
burying the evidence ever since.

- **Landform:** Big crescent dunes (barchans) that all face the same prevailing wind, broken
  up by flat salt pans and wind-carved **yardangs**, long streamlined rock ridges that look
  like stranded ships.
- **Palette:** Pale sand and sandstone, plus warm red sand and terracotta in banded mesas
  along the edges. Underneath it all is a layer of **fused glass** (tinted and amber stained
  glass, obsidian veins) that the wind uncovers in dune troughs.
- **Signature features:**
  - **Glass flats:** wide, glittering sheets of exposed glass that catch the sunset.
  - **Fulgurite forests:** branching glass spires where lightning once struck the sand.
  - **Sunken colonnades:** pillar tops sticking out of the dunes, part of a city that is
    still mostly buried.
  - **Oases** sit only where the terrain engine puts a basin below the water table, so
    they're rare, believable and valuable.
- **Variation:**
  - *Palette:* the color of the sky-fire decides the color of the glass. Amber, emerald,
    violet or blood-red glass, over white, gold or red sand.
  - *Form:* crescent dunes, star dunes, long parallel ridges, or a sea of dunes taller
    than most hills.
  - *State:* how much has been uncovered, from a few glints in the troughs to whole
    glass plains.
  - *Legendary:* the impact crater itself, a glass bowl hundreds of blocks wide.
- **Climate logic:** The Glass Waste forms downwind of the Spine, in its rain shadow. Where
  the two meet, mountain streams sink into the sand and fade out.
- **Mood:** silence, heat shimmer, and the feeling of walking over a grave.

### 3. Dragonscale: *The Sleeping Hides*

Land that is literally the body of something enormous. Whether it's dead or only sleeping
is left deliberately unclear. There isn't one dragon: the world holds **many** of these
creatures, each one a different body, and no two should ever look alike.

- **Landform:** Hills made of huge overlapping **scale plates**: tilted slabs 20–40 blocks
  across, each lifted at one edge and overlapping the next like roof tiles. From above the
  pattern is unmistakable. From the ground it reads as strange, terraced cliffs. Long
  **spinal ridges** of jagged spikes run through the region, and in rare places the land
  rises into the shape of a **colossal skull** or a **folded wing** (a curved, ribbed
  overhang that covers a whole valley).
- **Each Hide is one creature.** The engine first generates a **body plan**: a curving
  spine, a head, limbs, wings and a tail laid across the region in a pose. Then it builds
  the land from that plan. Scales follow the body's curves, ridges trace the spine, valleys
  sit between the ribs, and a lake might fill an eye socket. That makes each Hide one
  coherent body and not just a scaly texture.
- **Signature features:**
  - **Breath vents:** fissures between scales that sometimes release warm steam and
    particles, as if the land is breathing.
  - **Ember veins:** magma and glowing ore deep under the plates, the creature's slow heat.
  - **Shed scales:** single plates lying loose in nearby biomes, a hint of what's ahead.
- **Variation (this biome gets the deepest set of axes):**
  - *Creature lineage (form):*
    - **Wyrm:** wingless and serpentine, coiled in loops that make ring-shaped valleys.
    - **Drake:** four-legged and winged, lying with wings spread into huge ribbed overhangs.
    - **Leviathan:** sea-born, with fins and a long flat body that runs into lakes or the
      coast, where its back rises from the water as a chain of islands.
    - **Hydra:** several necks fanning out from one body, each ending in its own skull hill.
    - **Elder:** horned and crested, with spikes the size of towers. Legendary.
  - *Hide type:* plate scales, small dense mosaic scales, feather-like blade ridges, or
    smooth crystalline facets.
  - *Palette (curated hides):*
    - *Ash:* basalt, blackstone, with magma glowing in the seams.
    - *Verdigris:* deepslate with oxidized copper edges, moss in the cracks.
    - *Gilded:* sandstone and smooth stone with gold trim. Fits next to the Glass Waste.
    - *Frost:* packed ice, blue ice and calcite. Fits next to the Spine.
    - *Amethyst:* purple-black tuff with amethyst crests that chime in the wind.
    - *Abyssal:* prismarine and dark prismarine. Fits the Leviathan.
    - *Bone:* bone blocks and calcite, a creature stripped to its skeleton.
  - *Pose:* curled asleep, sprawled flat, half-buried mid-crawl, or reared up with its
    head raised as a mountain.
  - *Scale:* from a wyrmling covering a few hills to a great beast spanning several
    regions.
  - *State:* freshly fallen and sharp, fossilized and weathered, overgrown by forest so
    only the silhouette shows, or crystallized.
- **Engine note:** This is our showcase biome. Body plan plus scale field plus palette is
  something that only works because terrain and biome are one system.
- **Mood:** wonder mixed with dread. You're walking on something alive, and you notice
  it's quiet.

### 4. Demonic: *The Scar*

The place where the world was torn open and something came through. It's an injury, not a
Nether copy.

- **Landform:** A huge **rift** of stepped chasms and inward-tilting cliffs that all
  point toward one central wound. The land around it is **pulled downward**, warped
  like cloth pinched from below. Floating **torn-off islands** hang above the rift and
  trail chains of rubble.
- **Corruption gradient:** The Scar doesn't have a clean edge. It spreads outward along a
  corruption field that the engine shares with neighbouring biomes:
  - *Outer blight:* grass turns ashen and trees become leafless and black.
  - *Inner wound:* soul sand, blackstone, crimson growth and veins of lava under a crust.
  - *The Maw:* the deepest point, a vertical shaft with obsidian ribs and a glowing,
    unreachable floor.
- **Signature features:**
  - **Inverted spires:** stalactite-like towers that hang from the floating islands.
  - **Bleeding stone:** red-stained rock (redstone ore, crimson nylium) running down cliff
    faces.
  - **Chain bridges:** natural strands of dark iron/blackstone linking the islands, as
    if something tried to stitch the wound shut.
- **Variation:**
  - *Palette (what came through):* crimson and lava, warped teal and soul fire, or a
    void-black, purple and obsidian breach.
  - *Form:* one deep maw, a long straight rift like a sword cut, or a ring of smaller
    wounds around a raised center.
  - *State:* fresh and spreading, or old and scabbed over, with life slowly returning.
  - *Legendary:* a Scar that has sealed, leaving a glassy obsidian scar and a silent
    crater.
- **Mood:** wrongness. The geometry should feel slightly off, with cliffs leaning the
  wrong way and too many right angles. It's meant to be dangerous and tempting.

### 5. Floating Islands: *The Stilled Colossi*

Huge islands drifting above a lake as clear as crystal. They were once the home of a race of
giant automatons. The automatons are still there, frozen mid-motion and slowly eroding into
the rock.

- **Landform:** A wide, shallow **crystal lake** with a floor of pale quartz and calcite
  you can see through clear water. Above it, **floating islands** in several layers, from
  small rocks to islands big enough for a forest, each with a rough, tapering underside.
  Waterfalls pour off their edges and turn to mist long before they reach the lake.
- **The Colossi:** Automatons hundreds of blocks tall, part machine and part landscape:
  - One stands waist-deep in the lake, **holding an island up** on its shoulders.
  - One kneels, its head bowed, and moss and trees have taken over its back.
  - Some have fallen apart into pieces, so a giant hand becomes a hill and a chest cavity
    becomes a cave with glowing machinery inside.
  - A few are **built into** the islands themselves, chained to them or welded into the
    rock, suggesting the islands were their work.
- **Signature features:**
  - **Crystal reflections:** a mirror-still lake that doubles every island and colossus
    above it.
  - **Gear terraces:** huge exposed gears, half-buried, that became terraced hillsides.
  - **Core light:** a soft glow (sea lanterns, shroomlight, amethyst) still burning
    inside the chests of the colossi.
  - **Tether chains:** iron chains linking islands to each other or anchoring them to
    the lakebed.
- **Variation:**
  - *Palette (automaton make):* brass and copper (oxidized to every stage), white stone
    and gold, dark iron and blackstone, or crystal-bodied, made of amethyst and glass.
  - *Form:* humanoid giants, four-legged walkers, serpentine constructs, or towering
    engines with no body shape at all.
  - *Island density:* a few huge islands vs. a whole archipelago scattered across the sky.
  - *State:* nearly intact and gleaming, or so worn that only the posture is left.
  - *Legendary:* a colossus whose core still pulses, and the islands around it drift very
    slowly.
- **Mood:** serene and melancholy. A civilization that stopped mid-sentence.

### 6. Titan Forest: *The Canopy Cathedral*

A forest where the trees are as big as mountains. Under the canopy it's a cathedral, with
light falling in shafts through a roof hundreds of blocks above you.

- **Landform:** Trees 150–300 blocks tall with trunks 20–40 blocks across. Their roots form
  **arches** you can walk under, and their branches cross between trees like natural bridges.
  The forest floor is dim and rolling, full of fallen trunks the size of hills.
- **Layers:**
  - *Floor:* ferns, moss, giant mushrooms, and bioluminescent glow in the deep shadow.
  - *Mid-trunk:* hollow trunks with spiral paths inside, shelf fungi big enough to stand on.
  - *Canopy:* a separate world on top. Leaf meadows, pools caught in branch forks, and
    flowers in the open sun.
- **Signature features:**
  - **Light wells:** gaps where one great tree fell, letting a pillar of daylight hit the
    floor.
  - **Fallen giants:** collapsed trunks that became ridges, with a new forest growing on
    top.
  - **Root cathedrals:** clusters of arches so tall and regular they look built.
- **Variation:**
  - *Palette (tree kind):* evergreen giants (spruce, dark oak), golden autumn giants,
    pale white-barked giants (birch), or crimson-leaved ones near the Scar.
  - *Form:* straight columns, twisted banyan-like trunks with many roots, or trees that
    split into several crowns.
  - *State:* healthy and dense, burned with blackened skeletons and new green growth, or
    flooded with the trunks rising from still water.
  - *Legendary:* one world tree, far taller than the rest, its crown visible from any
    nearby region.
- **Mood:** reverent and small. You are an insect in a church.

### 7. Coast: *The Frozen Wave*

A tsunami that was stopped mid-crash. A cliff of ice hundreds of blocks high curls over the
coastline, frozen in the instant before it broke.

- **Landform:** A huge ice **breaker** running along the coast, curling forward into an
  overhang that roofs over the land below it. In front, a sea frozen into choppy, jagged
  waves. Behind it, the open ocean, still moving.
- **Signature features:**
  - **The Curl:** the tunnel under the overhang, lit blue by light shining through the ice.
  - **Spray spires:** frozen fountains of water flung high above the crest, now ice
    needles.
  - **Caught things:** ships, whales and debris trapped inside the ice, visible through
    it.
  - **The village under the wave:** coastal ruins sheltered and half-crushed under the
    overhang.
- **Variation:**
  - *Palette:* glacier blue (blue and packed ice), pale green ice, or dark stormy ice with
    sediment bands.
  - *Form:* a single towering curl, a series of smaller waves in a row, or a broken wave
    that has already collapsed partly onto the land.
  - *State:* sharp and fresh, or melting, with waterfalls running off its face.
  - *Legendary:* a wave frozen in the middle of the ocean, a curling ice island far from
    any coast.
- **Mood:** time standing still. Pure spectacle.

### 8. Terraced Falls: *The Thousand Stairs*

A whole mountainside of stepped pools, each spilling over its rim into the one below. It's
a staircase of water, hundreds of steps and hundreds of blocks high.

- **Landform:** Cascades of **travertine terraces**, shallow pools with curved, raised rims
  stacked down the slopes, like rice terraces at the scale of a mountain. Water runs
  down every step and gathers in huge basins at the bottom.
- **Signature features:**
  - **The Great Fall:** one drop at the top, where a river comes over a cliff and feeds
    the whole staircase.
  - **Mist valleys:** valleys so full of spray they're permanently foggy, with rainbows
    (particles) in the morning.
  - **Mineral colors:** each tier stained a different color by the minerals in the water.
- **Variation:**
  - *Palette:* white travertine and turquoise water (calcite, diorite), rust-red and
    orange iron terraces (terracotta), or green-gold mossy terraces.
  - *Form:* a tall, narrow staircase down a cliff, a wide amphitheatre of terraces, or
    terraces spiraling down into a sinkhole.
  - *State:* active and overflowing, or dried out, with empty basins and stained rims.
  - *Legendary:* terraces fed by a hot spring, steaming, with warm blue water glowing at
    night.
- **Climate logic:** The Thousand Stairs form on the *wet* side of the Spine, the opposite
  side from the Glass Waste, where the rain that never reaches the desert comes down.
- **Mood:** abundance and calm. A place to build.

### 9. Wetland: *The Lantern Marsh*

A huge, warm wetland of oversized plants that lights up at night. In the daytime it's lush
and green. At night it glows like a city.

- **Landform:** Wide, shallow water broken up by islands of root mats, with mangrove-like
  trees whose arching roots stand tall enough to walk under. Channels meander through it,
  and **lily pads** dozens of blocks across float on the open lagoons.
- **Signature features:**
  - **Lantern blooms:** huge flowers and fungi (shroomlight, glowstone, ochre and
    pearlescent froglights) that light up at night.
  - **Firefly clouds:** particle swarms over the water at dusk.
  - **Root halls:** dense groves where roots form tall, enclosed halls over the water.
  - **Bloom season:** some areas of the marsh are a sea of color.
- **Variation:**
  - *Palette (glow color):* warm gold, cool blue-green, pink-violet, or a mix.
  - *Form:* open lagoons with scattered giant pads, dense root forest, or a flooded
    plain dotted with tree-islands.
  - *State:* healthy, or encroached on by corruption near the Scar, where the glow turns
    red and sickly.
  - *Legendary:* one bloom large enough to be a hill, opening at night.
- **Climate logic:** The Lantern Marsh fills the deltas and basins where the water from the
  Thousand Stairs ends up.
- **Mood:** wonder and warmth. The world's gentlest place, and its prettiest at night.

---

## Structures

Structures are planned together with the terrain and should feel like they grew out of
the land's history. Structures take on the variant traits of the region around them, so a
Scale-Warden Shrine on a Frost Hide is built of ice and calcite, and one on an Ash Hide of
blackstone.

| Structure | Home | Idea |
|---|---|---|
| **Pass Fortress** | Spine | A stone keep built into the narrowest pass, with walls that run along the ridgeline. |
| **Hermit's Cirque** | Spine | A small hut on a frozen tarn, with an ice-fishing hole and a telescope pointed at the Scar. |
| **The Drowned Sun Temple** | Glass Waste | A ziggurat that is mostly buried. Its glass dome is the "sun" that fell. Dunes shift around it. |
| **Caravan Wells** | Glass Waste | Stone well-houses linked by faint roads that the engine routes between oases. |
| **Scale-Warden Shrine** | Dragonscale | A monastery wedged under a lifted scale, built by monks who keep the creature asleep. |
| **The Skull Hollow** | Dragonscale | A dungeon inside the colossal skull's eye socket. |
| **Binding Pylons** | Edge of the Scar | Ancient obelisks set in a ring around the rift. Some are broken, and the corruption spreads further behind the broken ones. |
| **Fallen Bastion** | Scar islands | A fortress that fell into the rift and is now stranded on a floating island. |
| **The Core Chamber** | Stilled Colossi | A dungeon inside a colossus's chest, built around its still-glowing heart. |
| **Island Workshops** | Stilled Colossi | Abandoned forges on the islands where the automatons were built, with half-finished limbs on the floor. |
| **Canopy Village** | Canopy Cathedral | Platforms and rope bridges in the crown of a titan tree, the only place in the biome with full sun. |
| **The Hollow Trunk** | Canopy Cathedral | A spiral stair inside a dead giant, leading from the roots to the canopy. |
| **Wave Watch** | Frozen Wave | A lighthouse on the crest of the wave, built by those who survived it. |
| **Bathhouse Terraces** | Thousand Stairs | Carved stone baths and colonnades built into the terraces. |
| **Stilt Town** | Lantern Marsh | A village on stilts and lily pads, lit up at night by its gardens of lantern blooms. |

## How it fits together

The biomes tell one connected history of the world, in layers:

1. **The Spine** is the old backbone of the world. It's stable, cold and indifferent. It
   splits the climate in two: rain falls on one side and feeds **the Thousand Stairs**,
   whose water drains into **the Lantern Marsh**. The other side lies in its rain shadow,
   where the **Glass Waste** forms.
2. **The Stilled Colossi** are the world's first civilization. The automatons lifted the
   islands and built in the sky, then stopped all at once. Nobody knows why, but the
   timing lines up with the sky-fire.
3. **The Glass Waste** is the scar of that *old* catastrophe, the sky-fire. Over the ages
   it has healed into a desert.
4. **The Sleeping Hides** are what that sky-fire might have been, or what fought it.
   Dragons that fell and became land. Dragonscale regions often border the Glass Waste,
   with plates sinking under the dunes. The Frozen Wave may be another mark of the same
   day, a sea struck so hard it froze.
5. **The Canopy Cathedral** grew in the long, quiet age that followed, when the world
   healed and things were allowed to become enormous.
6. **The Scar** is the *current* catastrophe, a fresh wound that is still spreading. The
   Binding Pylons and the Scale-Wardens hint that the old powers are the only thing holding
   it back.

**Placement rules this implies for the engine:**
- The Glass Waste forms in the rain shadow of the Spine. The Thousand Stairs form on its
  wet side, and the Lantern Marsh where their water collects.
- Dragonscale prefers the border between the Glass Waste and the foothills, and picks its
  palette from its neighbours (Frost near the Spine, Gilded near the desert, Abyssal on
  coasts).
- The Stilled Colossi need a wide, flat basin for their lake, often in the shelter of
  the Spine's foothills.
- The Canopy Cathedral needs warm, wet lowland. The Frozen Wave only appears on cold
  coasts.
- The Scar is rare (about one per very large region). Its corruption field fades
  neighbouring biomes, pushing them into their corrupted variants, and it is never placed
  right next to Dragonscale. Something keeps them apart.
- Transitions blend landforms, not just block palettes. Every border should be a place
  worth exploring.
