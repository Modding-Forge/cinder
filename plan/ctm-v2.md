# CTM-v2 / CTM-v3 Performance Plan

Stand: 2026-06-17

Diese Datei dokumentiert den aktuellen CTM-Performance-Stand und den
geplanten naechsten Architektur-Schritt. Der Name `CTM-v2` beschreibt den
Sodium-ModelEmitter-Umbau als laufende Integrationsphase. Der eigentliche
naechste Optimierungsschritt wird hier als `CTM-v3` bezeichnet:
Block-/Face-lokale CTM-Planung statt voller Resolve-Arbeit pro Quad.

## Ausgangslage

Argus rendert CTM im Sodium-Terrain-Pfad. Der bisherige spaete
`BlockRenderer.processQuad(...)`-Pfad wurde durch einen frueheren Sodium
`PlatformModelEmitter`-Pfad vorbereitet. Dadurch kann Argus pro Blockmodel
einen `ArgusSodiumBlockRenderPlan` erzeugen und Quads durch eine eigene kleine
Pipeline leiten.

Aktueller wichtiger Pfad:

- `ArgusSodiumModelEmitter`
- `ArgusSodiumBlockRenderPlan`
- `ArgusSodiumQuadPipeline`
- `CtmSodiumQuadProcessor`
- `CtmRenderResolver`
- `CtmRenderIndex`

Der ModelEmitter-Umbau ist ein saubererer Einstiegspunkt, loest aber noch
nicht das eigentliche CTM-Kostenproblem. Der aktuelle Quad-Pfad ruft CTM
weiterhin pro Quad auf. `CtmRenderIndex` filtert bereits nach Sprite, Block
und Face, aber die teure Regel-/Predicate-Auswertung wird noch nicht pro
Block/Face/Sprite wiederverwendet.

## Benchmark-Evidenz

Benchmark-Setup:

- gleicher Seed: `329562103`
- neue Welt pro Run
- Fullscreen
- VSync aus
- FOV Quake Pro
- Max Framerate unlimited (`maxFps:260`)
- Render Distance 20
- alle Argus-Features aktiv
- aktive Packs:
  - Argus Default Pack
  - `Connected-Bricks 1.21.9+ v1.0.zip`
  - `Connected-Paths 26.1 - 26.1.2 v2.1.1.zip`
  - `Connected-Rocks 1.21.9+ v1.1.1.zip`
  - `Dramatic Skys Demo 1.5.3.36.4.zip`

Reports:

- `build/argus-benchmark/reports/ctm-v2-fullscreen-20260617`
- `build/argus-benchmark/reports/master-off-20260617`

Ergebnis der 12 CTM-v2-Vergleichsruns:

| Loader | Pfad | Avg FPS | Median FPS | P05 | P95 |
| --- | --- | ---: | ---: | ---: | ---: |
| Fabric | PlatformModelEmitter | ca. 595 | ca. 593 | ca. 319 | ca. 847 |
| Fabric | legacy processQuad | ca. 596 | ca. 582 | ca. 307 | ca. 847 |
| NeoForge | PlatformModelEmitter | ca. 652 | ca. 645 | ca. 341 | ca. 966 |
| NeoForge | legacy processQuad | ca. 586 | ca. 594 | ca. 365 | ca. 761 |

Interpretation:

- Der neue Emitter-Pfad ist fuer Fabric ungefaehr neutral.
- Der neue Emitter-Pfad hilft NeoForge sichtbar.
- Der grosse Kostenblock bleibt CTM-Resolve, nicht der Hook selbst.

Master-Off Baseline mit `argus.enabled=false`:

| Loader | Avg FPS | Median FPS | P05 | P95 |
| --- | ---: | ---: | ---: | ---: |
| Fabric | ca. 805 | ca. 786 | ca. 556 | ca. 1202 |
| NeoForge | ca. 759 | ca. 788 | ca. 461 | ca. 917 |

Wichtige Bucket-Beobachtung:

- Mit allen Features aktiv dominiert `ctm.resolve`.
- Master-Off hat `ctm.resolve = 0`.
- `ctm.prefilter`, `ctm.neighbor_view` und `ctm.overlay_plan` sind deutlich
  kleiner als `ctm.resolve`.

Schlussfolgerung: Die naechste grosse Optimierung muss die Anzahl und Tiefe
der CTM-Resolve-Aufrufe reduzieren. Noch cleverere Einzelchecks im bestehenden
pro-Quad-Resolver reichen nicht.

## Zielarchitektur

Aktueller Ablauf:

```text
Block -> Quad -> CTM Prefilter -> NeighborView -> Resolver
      -> Selection -> Sprite/Overlay
```

Ziel:

```text
Block -> ArgusSodiumBlockRenderPlan
      -> lazy Face/Sprite CTM Result Cache
      -> Quad konsumiert fertiges Result
```

Der Kern: CTM wird pro Block/Face/Sprite vorbereitet und pro Quad nur noch
angewendet. Mehrere Quads derselben Face und Sprite teilen sich dann dieselbe
CTM-Auswahl.

## CTM-v3 Schritt 1: Block-local Result Cache

Neue Client-Typen in `src/client`:

- `ArgusCtmBlockPlan`
- `ArgusCtmFacePlan`
- `ArgusCtmFaceSpriteResult`

`ArgusSodiumBlockRenderPlan` bekommt einen CTM-Plan:

```java
private final ArgusCtmBlockPlan ctmPlan;
```

Der Blockplan enthaelt:

- Config-Snapshot
- `BlockAndTintGetter`
- `BlockState`
- `BlockPos`
- Block-ID
- lazy `CtmMinecraftNeighborView`
- sechs Face-Slots
- pro Face eine kleine lineare Sprite-Result-Liste

Keine `HashMap` pro Block. Ein normales Blockmodel hat wenige Sprites; kleine
Arrays oder feste Slots sind billiger und vorhersehbarer.

Quad-Ablauf:

1. Face aus Cull/Nominal/Light Face bestimmen.
2. Source-Sprite bestimmen.
3. `blockPlan.ctm().resultFor(face, sourceSprite)` abfragen.
4. Wenn vorhanden: Result direkt anwenden.
5. Wenn nicht vorhanden: CTM einmal resolven und Result speichern.

Dieser Schritt aendert die CTM-Semantik nicht. Er reduziert nur doppelte Arbeit
innerhalb desselben Blockmodel-Emits.

## CTM-v3 Schritt 2: Processor Split

`CtmSodiumQuadProcessor.prepare(...)` ist aktuell zu breit. Es erledigt:

- Feature-Gates
- Full-Face-/Planar-Checks
- Sprite-/Block-ID-Lookups
- Kandidatenabfrage
- NeighborView-Aufbau
- Resolver
- Glass-Sonderfaelle
- Overlay-Plan
- Material-/Sprite-Lookup
- UV-Remap

Das soll in zwei Ebenen getrennt werden:

### Resolve-Ebene

Neue Methode, sinngemaess:

```java
CtmResolvedFaceResult resolveBlockFaceSprite(...);
```

Aufgabe:

- Prefilter
- NeighborView
- `CtmRenderResolver`
- Replacement/Overlay-Selection
- nur quad-unabhaengige Daten sammeln

### Apply-Ebene

Neue Methode, sinngemaess:

```java
boolean applyResolvedResultToQuad(...);
```

Aufgabe:

- Full exterior replacement gate
- Full planar overlay gate
- Glass interior discard
- Compact replacement shaping
- Material-/Sprite-Lookup
- Overlay-Sodium-Plan
- UV-Remap

Dadurch kann der teure Resolve-Teil gecached werden, waehrend
quad-spezifische Geometrieentscheidungen korrekt bleiben.

## CTM-v3 Schritt 3: Shared Runtime Profiles

Nach dem risikoarmen Cache-Schritt wird `src/shared` erweitert.

Neue shared Typen:

- `CtmRuleRuntimeProfile`
- `CtmConnectivityProfile`
- `CtmMaskScratch`
- optional `CtmLookupTables`

Jede Regel bekommt beim Reload ein Runtime-Profil:

- Methode
- benoetigte Nachbarschaft:
  - `NONE`
  - `HORIZONTAL_2`
  - `VERTICAL_2`
  - `CARDINAL_4`
  - `FULL_8`
- Connect-Mode
- Inner-Seams-Flag
- Random-/Repeat-/Fixed-Fastpath
- optionaler Predicate-Key zur Deduplizierung

Ziel: Regeln mit gleichem Connectivity-Profil teilen dieselbe berechnete
Maske.

## CTM-v3 Schritt 4: Layout-spezifische Masken

Nicht jede CTM-Methode braucht acht Nachbarn.

| Methode | benoetigte Checks |
| --- | --- |
| `fixed` | 0 |
| `random` | 0 fuer Connectivity, Seed bleibt positionsbasiert |
| `repeat` | 0 fuer Connectivity |
| `horizontal` | 2 |
| `vertical` | 2 |
| `top` | 1 |
| `horizontal+vertical` | 4 |
| `vertical+horizontal` | 4 |
| `ctm` | 4 bis 8 |
| `ctm_compact` | 4 bis 8 |
| `overlay_ctm` | 4 bis 8 |

Diagonalen sollen lazy bleiben:

- erst Side-Mask berechnen
- Diagonalen nur berechnen, wenn die Methode sie braucht
- ohne `innerSeams` Diagonalen nur pruefen, wenn die angrenzenden Seiten
  verbunden sind

## CTM-v3 Schritt 5: Lookup-Tabellen

Sprite-Auswahl soll im Hotpath moeglichst ein Array-Lookup sein.

Beim Resource Reload vorbereiten:

- `ctm`: `byte[256]` oder `short[256]`
- `ctm_compact`: `byte[256]`
- `horizontal`: kleines Lookup
- `vertical`: kleines Lookup
- `horizontal+vertical`: methodenspezifisches Lookup
- `vertical+horizontal`: methodenspezifisches Lookup
- Overlay-CTM: vorberechnete Tile-Sets, soweit semantisch sicher

Hotpath-Ziel:

```java
int tile = profile.lookup[mask & profile.maskDomain];
```

statt mehrfacher Branches und Selector-Logik.

## CTM-v3 Schritt 6: Overlay-Allokationen entfernen

`CtmRenderScratch.addOverlay(...)` erzeugt aktuell `CtmOverlayTile`-Objekte.
Bei Overlay-lastigen Packs ist das unnoetig.

Geplanter Umbau:

- `CtmRenderScratch` speichert im Hotpath parallele Arrays:
  - `CtmRule[] overlayRules`
  - `int[] overlayTileIndices`
- `CtmOverlayTile` bleibt nur fuer immutable/public Plan-APIs.
- Sodium liest direkt `overlayRule(i)` und `overlayTileIndex(i)`.

Das ist nicht der groesste CPU-Hebel, reduziert aber GC-Risiko und Hotpath-
Rauschen.

## CTM-v3 Schritt 7: Optionaler Nachbarstate-Cache

Erst nach den vorherigen Schritten:

- pro Blockplan ein kleiner 3x3x3-Cache fuer BlockStates/Block-IDs
- lazy Fuellung
- keine direkte Bindung an Sodium-Section-Interna im ersten Schritt

Ziel:

- weniger wiederholte `level.getBlockState(...)`-Zugriffe
- weniger `BlockPos`-/Mutable-Position-Arbeit
- gleiche Semantik wie bisher

Ein tiefer Chunk-/Section-Slice-Cache ist ein spaeterer Schritt und soll erst
kommen, wenn die Block-local Variante nicht reicht.

## Semantik-Leitplanken

Diese Regeln duerfen durch den Performance-Umbau nicht veraendert werden:

- Sprite-Regeln gewinnen vor Block-Fallback.
- Overlay-Reihenfolge bleibt stabil.
- Replacement-CTM gilt nur fuer volle Exterior-Faces.
- Overlay-CTM darf auch volle planare Faces treffen.
- Glass interior discard bleibt identisch.
- Compact CTM Quadranten bleiben visuell identisch.
- Random bleibt deterministisch pro Position/Face/Regel.
- Repeat bleibt positionsstabil.
- Tint/AO/Layer-Verhalten von Overlays bleibt gleich.
- Fabric und NeoForge verwenden denselben `src/client`-Pfad.

## Benchmark-Gates

Nach jedem relevanten Schritt:

```powershell
.\gradlew.bat :src:shared:test
.\gradlew.bat :src:fabric:build :src:neoforge:build
```

Danach mindestens:

- Fabric all-features Benchmark
- NeoForge all-features Benchmark
- optional Master-Off Baseline zur Kontrolle des Framework-Overheads

Akzeptanz:

- keine sichtbare Regression
- `ctm.resolve` sinkt sichtbar
- `sodium.process_quad` steigt nicht relevant
- Fabric und NeoForge profitieren oder bleiben neutral

## Ingame-Smoke

Beide Loader:

- Builtin regular glass CTM
- PureBDCraft CTM Addon Overlays
- Connected-Paths
- Connected-Bricks
- Connected-Rocks
- Better Grass Fast/Fancy kurzer Spotcheck
- Better Snow Fake Snow Layer kurzer Spotcheck
- Natural Textures Toggle kurzer Spotcheck

## Risiken

### Zu fruehes Caching falscher Daten

Ein Face/Sprite-Result darf nur Daten cachen, die wirklich quad-unabhaengig
sind. Full-Face, Planar-Face, Backface und Glass-Interior-Entscheidungen
bleiben teilweise quad-spezifisch.

### Random/Repeat

Random und Repeat duerfen nicht mit Connectivity-Gruppen vermischt werden. Sie
brauchen eigene Fastpaths.

### Overlay-Stapel

Overlay-Regeln sind kompositorisch. Ein Replacement beendet nicht automatisch
alle vorher passenden Overlays. Diese Reihenfolge muss erhalten bleiben.

### Debugbarkeit

CTM-Debug-Ausgaben muessen weiterhin erklaeren koennen, welche Regel und
welcher Tile-Index gewaehlt wurden. Cache-Treffer sollten bei Debug optional
erkennbar sein, aber nie im normalen Hotpath loggen.

## Empfohlene Umsetzung

Nicht alles in einem Schritt.

### Commit 1: Block-local Result Cache

- `ArgusCtmBlockPlan`
- `ArgusCtmFacePlan`
- `ArgusCtmFaceSpriteResult`
- `ArgusSodiumBlockRenderPlan` erweitert
- `CtmSodiumQuadProcessor` minimal splitten
- keine `CtmSelector`-Semantik aendern

### Commit 2: Resolve/Apply sauber trennen

- CTM-Resolve erzeugt ein cached Result
- Quad-Apply konsumiert Result
- Bench-Buckets fuer Cache-Hits/Misses ergaenzen

### Commit 3: Runtime Profiles

- `CtmRuleRuntimeProfile`
- benoetigte Masken pro Methode
- Resolver gruppiert Regeln nach Profil

### Commit 4: Lookup-Tabellen

- 256er Tabellen fuer CTM/Compact
- kleine Tabellen fuer Horizontal/Vertical
- Unit-Tests gegen bestehende Tile-Oracle-Suite

### Commit 5: Overlay-Scratch ohne Objektallokation

- parallele Arrays im Hotpath
- Public-Plan-Kompatibilitaet erhalten

### Commit 6: Neighbor Cache

- 3x3x3 Block-local Cache
- nur wenn Benchmarks nach Commit 1-5 noch zeigen, dass Nachbarzugriffe
  relevant sind

## Erwartung

Der ModelEmitter-Umbau ist die richtige Grundlage, aber nicht der Endgewinn.
Der erwartete grosse FPS-Gewinn kommt erst, wenn Argus die ModelEmitter-
Moeglichkeit nutzt, CTM pro Block/Face/Sprite vorzubereiten.

Wenn CTM-v3 korrekt umgesetzt ist, sollte Argus bei CTM-lastigen Packs deutlich
naeher an der Master-Off-Baseline liegen, ohne Visuals einzubueszen.

## Implementierungsstand 2026-06-17

Status: **Unit-verified und benchmark-verifiziert fuer den ersten
Hotpath-Batch.** Noch nicht als vollstaendig abgeschlossen markieren, weil die
weiteren CTM-v3-Schritte aus diesem Dokument noch offen sind.

Implementiert:

- Sodium `PlatformModelEmitter`-Pfad bleibt aktiv.
- `ArgusSodiumBlockRenderPlan` erzeugt pro Block einen CTM-Plan.
- `ArgusCtmBlockPlan`, `ArgusCtmFacePlan` und
  `ArgusCtmFaceSpriteResult` cachen CTM-Ergebnisse pro Block/Face/Sprite.
- `CtmSodiumQuadProcessor` trennt teuren CTM-Resolve von
  quad-spezifischem Apply.
- `ArgusBenchmark` zaehlt `ctm.face_cache_hit` und
  `ctm.face_cache_miss`.
- `CtmMinecraftNeighborView` cached fallback `NamespaceId`-Sprites in der
  bestehenden Sprite-Tabelle, statt sie pro Zugriff neu zu erzeugen.
- `CtmSelector` cached Center-Block, Center-Sprite und Center-FullBlock fuer
  die Dauer eines `resolveCandidatesInto(...)`-Aufrufs.
- `CtmRenderScratch` speichert Overlay-Ergebnisse im Hotpath als parallele
  `CtmRule[]`/`int[]`-Arrays; `CtmOverlayTile` bleibt nur fuer public
  Plan-Kompatibilitaet.
- `ArgusCtmFaceSpriteResult` cached Overlay-Regel und Tile-Index ebenfalls
  direkt, damit Sodium keine Overlay-Records pro gecachtem Result erzeugt.
- `CtmRuleRuntimeProfile` und `CtmConnectivityProfile` werden beim
  Rule-Aufbau vorbereitet. Resolver, Selector und RenderIndex nutzen die
  Profil-Traits bereits fuer Overlay-/Layer-Kategoriechecks. Die eigentliche
  Gruppierung nach Connectivity-Profil ist vorbereitet, aber noch nicht als
  Masken-Reuse-Fastpath umgesetzt.
- Gewichtete `random`-Regeln kompilieren ihren `WeightedSelector` jetzt beim
  Rule-Aufbau. `selectRandom(...)` sampled nur noch den vorbereiteten Selector,
  statt im Hotpath `Integer[]` und `WeightedSelector` neu zu erzeugen. Das ist
  Unit-/Build-verifiziert; die aktuellen Benchmark-Packs enthalten keine
  `weights=`-CTM-Regeln, daher gibt es dafuer keinen aussagekraeftigen
  All-Features-Benchmark.

Explizit verworfen:

- Ein Overlay-Masken-Cache, der `maySelectConnectedOverlay(...)` und
  `selectOverlayTiles(...)` enger koppeln sollte. Ergebnis: real langsamer als
  der Overlay-Array-Stand; entfernt.
- Ein naiver `CtmConnectivityProfile.NONE`-Fastpath fuer
  `fixed`/`random`/`repeat` und entsprechende Overlay-Varianten. Ergebnis:
  zusaetzlicher Branch pro Regel war teurer als die gesparte Arbeit; entfernt.
- Vorberechnete 3D-Array-Lookups fuer CTM/Compact-Tiles innerhalb
  `CtmSelector`. Ergebnis: Oracle gruen, Hotpath deutlich langsamer; entfernt.
- Das Entfernen des Overlay-Vorfilters vor
  `selectOverlayInto(...)`. Ergebnis: der Vorfilter ist in realen Packs der
  entscheidende Billigpfad; Entfernen machte `ctm.resolve` massiv teurer.
  Der Vorfilter bleibt.

Validierte Commands:

```powershell
.\gradlew.bat :src:shared:test :src:fabric:build :src:neoforge:build
.\gradlew.bat :src:shared:test --tests com.argus.ctm.CtmRuleRuntimeProfileTest --tests com.argus.ctm.CtmSelectorTest --tests com.argus.ctm.CtmPropertiesTest
.\gradlew.bat :src:fabric:build :src:neoforge:build
```

Gueltige Benchmark-Reports:

- `build/argus-benchmark/reports/ctm-v3-view-cache-20260617/20260616-234804-fabric-view-cache-1.json`
- `build/argus-benchmark/reports/ctm-v3-view-cache-20260617/20260616-234926-neoforge-view-cache-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-arrays-20260617/20260616-235545-fabric-overlay-arrays-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-arrays-20260617/20260616-235715-neoforge-overlay-arrays-1.json`

Verworfene Benchmark-Reports:

- `build/argus-benchmark/reports/ctm-v3-none-fastpath-20260617/20260617-001109-fabric-none-fastpath-1.json`
- `build/argus-benchmark/reports/ctm-v3-none-fastpath-20260617/20260617-001232-neoforge-none-fastpath-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-mask-cache-20260617/20260617-001805-fabric-overlay-mask-cache-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-mask-cache-20260617/20260617-001945-neoforge-overlay-mask-cache-1.json`
- `build/argus-benchmark/reports/ctm-v3-lookup-tables-20260617/20260617-002819-fabric-lookup-tables-1.json`
- `build/argus-benchmark/reports/ctm-v3-lookup-tables-20260617/20260617-002948-neoforge-lookup-tables-1.json`
- `build/argus-benchmark/reports/ctm-v3-no-overlay-prefilter-20260617/20260617-003359-fabric-no-overlay-prefilter-1.json`
- `build/argus-benchmark/reports/ctm-v3-no-overlay-prefilter-20260617/20260617-003525-neoforge-no-overlay-prefilter-1.json`
- `build/argus-benchmark/reports/ctm-v3-lazy-side-sprites-20260617/20260617-004210-fabric-lazy-side-sprites-1.json`
- `build/argus-benchmark/reports/ctm-v3-lazy-side-sprites-20260617/20260617-004340-neoforge-lazy-side-sprites-1.json`
- `build/argus-benchmark/reports/ctm-v3-connected-overlay-hotpath-20260617/20260617-004958-fabric-connected-overlay-hotpath-1.json`
- `build/argus-benchmark/reports/ctm-v3-connected-overlay-hotpath-20260617/20260617-005120-neoforge-connected-overlay-hotpath-1.json`
- `build/argus-benchmark/reports/ctm-v3-candidate-flags-20260617/20260617-005535-fabric-candidate-flags-1.json`
- `build/argus-benchmark/reports/ctm-v3-candidate-flags-20260617/20260617-005658-neoforge-candidate-flags-1.json`
- `build/argus-benchmark/reports/ctm-v3-primitive-selection-20260617/20260617-010542-fabric-primitive-selection-1.json`
- `build/argus-benchmark/reports/ctm-v3-primitive-selection-20260617/20260617-010655-neoforge-primitive-selection-1.json`
- `build/argus-benchmark/reports/ctm-v3-connecttile-block-first-20260617/20260617-124810-fabric-connecttile-block-first-1.json`
- `build/argus-benchmark/reports/ctm-v3-connecttile-block-first-20260617/20260617-124936-neoforge-connecttile-block-first-1.json`
- `build/argus-benchmark/reports/ctm-v3-tile-id-arrays-20260617/20260617-125507-fabric-tile-id-arrays-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-only-loop-20260617/20260617-130141-fabric-overlay-only-loop-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-only-loop-20260617/20260617-130305-neoforge-overlay-only-loop-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-resolve-cache-reuse-20260617/20260617-132128-fabric-overlay-resolve-cache-reuse-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-resolve-cache-reuse-20260617/20260617-132256-neoforge-overlay-resolve-cache-reuse-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-resolve-cache-toponly-20260617/20260617-132525-fabric-overlay-resolve-cache-toponly-1.json`
- `build/argus-benchmark/reports/ctm-v3-overlay-resolve-cache-toponly-20260617/20260617-132649-neoforge-overlay-resolve-cache-toponly-1.json`
- `build/argus-benchmark/reports/ctm-v3-empty-filter-fast-return-20260617/20260617-134943-fabric-empty-filter-fast-return-1.json`
- `build/argus-benchmark/reports/ctm-v3-empty-filter-fast-return-20260617/20260617-135103-neoforge-empty-filter-fast-return-1.json`
- `build/argus-benchmark/reports/ctm-v3-empty-filter-fast-return-20260617/20260617-135258-neoforge-empty-filter-fast-return-2.json`
- `build/argus-benchmark/reports/ctm-v3-direct-block-id-20260617/20260617-140854-fabric-direct-block-id-1.json`
- `build/argus-benchmark/reports/ctm-v3-direct-block-id-20260617/20260617-141015-neoforge-direct-block-id-1.json`

Vergleich gegen die vorherigen PlatformModelEmitter-Runs:

| Loader | Zustand | Avg FPS | Median FPS | `ctm.resolve` total | `ctm.resolve` avg |
| --- | --- | ---: | ---: | ---: | ---: |
| Fabric | vorher, 3 Run-Spanne | 522-686 | 540-668 | 329865-355883 ms | 98891-106143 ns |
| Fabric | CTM-v3 View Cache | 697.958 | 736 | 143665.924 ms | 25470.735 ns |
| Fabric | CTM-v3 Overlay Arrays | 696.437 | 751 | 133646.851 ms | 23497.926 ns |
| Fabric | verworfener NONE-Fastpath | 707.963 | 713 | 136833.334 ms | 23442.516 ns |
| Fabric | verworfener Overlay-Mask-Cache | 741.389 | 758 | 147530.915 ms | 26616.254 ns |
| Fabric | verworfene CTM-Lookup-Tabellen | 396.839 | 389 | 291076.151 ms | 91950.861 ns |
| Fabric | verworfener No-Overlay-Prefilter | 633.869 | 719 | 401678.396 ms | 804462.123 ns |
| Fabric | verworfene Lazy-Side-Sprites | 620.096 | 648 | 283841.559 ms | 87978.214 ns |
| Fabric | verworfener Connected-Overlay-Hotpath | 575.214 | 600 | 285196.005 ms | 92493.582 ns |
| Fabric | verworfene Candidate-Array-Flags | 415.613 | 432 | 291649.840 ms | 94977.425 ns |
| Fabric | verworfene primitive Selection | 585.553 | 604 | 285487.198 ms | 97185.775 ns |
| NeoForge | vorher, 3 Run-Spanne | 606-676 | 601-669 | 351799-357690 ms | 99128-110867 ns |
| NeoForge | CTM-v3 View Cache | 700.677 | 714 | 153205.116 ms | 26124.010 ns |
| NeoForge | CTM-v3 Overlay Arrays | 741.230 | 772 | 123529.985 ms | 21331.105 ns |
| NeoForge | verworfener NONE-Fastpath | 735.247 | 762 | 138491.037 ms | 25282.089 ns |
| NeoForge | verworfener Overlay-Mask-Cache | 729.908 | 745 | 138019.614 ms | 24052.084 ns |
| NeoForge | verworfene CTM-Lookup-Tabellen | 364.356 | 404 | 247443.416 ms | 79757.628 ns |
| NeoForge | verworfener No-Overlay-Prefilter | 552.577 | 658 | 394851.445 ms | 699388.106 ns |
| NeoForge | verworfene Lazy-Side-Sprites | 512.803 | 622 | 252785.478 ms | 80874.609 ns |
| NeoForge | verworfener Connected-Overlay-Hotpath | 337.997 | 394 | 270921.779 ms | 90011.841 ns |
| NeoForge | verworfene Candidate-Array-Flags | 369.080 | 427 | 270542.434 ms | 87063.250 ns |
| NeoForge | verworfene primitive Selection | 739.311 | 756 | 139094.110 ms | 24497.021 ns |

Interpretation:

- Der erste CTM-v3-Hotpath-Batch reduziert `ctm.resolve` total auf beiden
  Loadern um deutlich mehr als 50 Prozent.
- Der durchschnittliche `ctm.resolve`-Aufruf ist nun etwa viermal billiger.
- Die Overlay-Array-Migration reduziert zusaetzlich `ctm.overlay_plan` von
  ca. 272-277 ms auf ca. 200-207 ms und drueckt `ctm.resolve` weiter.
- Die Resolve-Anzahl steigt, weil mehr kleine Resolve-Segmente sichtbar werden;
  der Gesamtaufwand sinkt trotzdem massiv.
- Fabric und NeoForge profitieren beide im gemeinsamen `src/client`-Pfad.
- Der naive NONE-Fastpath wird nicht beibehalten: Er war nicht robust besser
  und verschlechterte mindestens einen Loader.
- Der Overlay-Mask-Cache wird nicht beibehalten: `ctm.resolve` und die
  uebergeordneten Sodium-Buckets wurden teurer.
- Der Overlay-Vorfilter bleibt: Ohne ihn wurden viele teure Overlay-Selects
  ausgefuehrt, die vorher billig verworfen wurden.
- Die CTM-Lookup-Tabellen werden nicht beibehalten: Sie waren deutlich
  langsamer als die bestehende kleine Branch-/Array-Logik.
- Lazy-Side-Sprites werden nicht beibehalten: weniger Sprite-Abfragen, aber
  schlechtere JIT-/Branch-Form und deutlich teurerer Resolve.
- Der kombinierte Connected-Overlay-Hotpath wird nicht beibehalten: weniger
  Resolve-Calls, aber deutlich teurere einzelne Resolves.
- Per-Array Candidate-Flags werden nicht beibehalten: zusaetzliche
  Dispatch-Form ohne messbaren Nutzen, aber mit hoeheren CTM-Kosten.
- Primitive Replacement-Selection wird nicht beibehalten: weniger Objektidee,
  aber schlechtere Hotpath-Form und schlechtere Benchmarks.
- ConnectTiles-Block-Fallback zuerst wird nicht beibehalten: Die Idee war,
  Sprite-Abfragen in den grossen Overlay-Stapeln zu sparen. Ergebnis:
  `ctm.resolve` wurde auf beiden Loadern schlechter; vermutlich war die
  bisherige Sprite-first-Form fuer JIT/Cache und reale Rule-Verteilung
  guenstiger als der zusaetzliche Kontrollfluss.
- Arraybasierte `connectTiles`-/`matchTiles`-Checks werden nicht beibehalten:
  Die oeffentlichen Listen wurden intern durch vorbereitete Arrays ergaenzt,
  aber Fabric fiel im Benchmark klar unter die aktuelle Basis. Der bisherige
  `List.contains(...)`-Pfad ist fuer die realen kleinen Tile-Listen offenbar
  nicht der Engpass.
- Ein spezialisierter Resolver-Loop fuer overlay-only Kandidatenarrays wird
  nicht beibehalten: Die Idee war, den Replacement/Overlay-Dispatch pro Regel
  zu sparen. Ergebnis: Fabric fiel auf 902.830 FPS mit 129051.7 ms
  `ctm.resolve`, NeoForge auf 769.100 FPS mit 139924.6 ms `ctm.resolve`.
  Damit ist die bestehende gemeinsame Resolver-Form schneller und stabiler.
  Damit bleibt der Overlay-Array-Stand die aktuell akzeptierte Basis.
- Ein blockuebergreifender Overlay-Resolve-Cache wird nicht beibehalten:
  Die breite Variante reduzierte zwar Resolve-Aufrufe, machte Fabric aber bei
  `ctm.resolve`, `sodium.ctm` und `sodium.process_quad` teurer. Die engere
  Top-Face/128-Regel-Variante lieferte bessere FPS, erfuellte aber das Ziel
  "geringeres `ctm.resolve`" nicht: Fabric stieg auf 122820.0 ms
  `ctm.resolve`, NeoForge auf 115215.1 ms. Der Cache-Key selbst ist zu teuer
  fuer die erzielte Trefferklasse und wurde per Revert entfernt.
- Ein Fast-Return nach dem `CtmNeighborRuleIndex`-Filter wird nicht
  beibehalten. Die Idee war, Kandidatensaetze abzubrechen, wenn der
  Neighbor-Index alle Block-Regeln entfernt hat. Fabric wurde besser, aber
  NeoForge wurde in zwei Runs bei `ctm.resolve` und `sodium.ctm` schlechter.
  Da Fabric und NeoForge gleichwertige Ziele sind, wurde der Code revertiert.

Naechste sinnvolle Schritte:

1. Benchmark-gestuetzten Candidate-/Rule-Analyse-Report bauen:
   Top-K Kandidatenarrays nach Aufrufzahl, Regelanzahl, Method-Verteilung,
   Overlay-Anteil, Connect-Modus und Condition-Typen. Ohne diese Daten keine
   weiteren Selector-Mikroexperimente. **Status: implementiert.**
2. Regeln im Resolver reload-seitig nach `CtmConnectivityProfile` gruppieren
   und Masken pro Profil wirklich wiederverwenden. Das darf kein zusaetzlicher
   Branch pro Regel sein, sondern muss kleinere, bereits sortierte Gruppen
   erzeugen.
3. Material-/Sprite-Realization fuer `(rule, tileIndex)` im Client cachen.
   Das reduziert nicht `ctm.resolve`, kann aber `sodium.ctm` und
   `sodium.process_quad` senken.

## Candidate-Analyse 2026-06-17

Status: **implementiert und einmal auf beiden Loadern verifiziert.**

Neue Benchmark-Daten:

- `ctmCandidateSets` im JSON-Report
- `CTM Candidate Sets` im Markdown-Report
- Runtime-Flag: `argus.benchmark.ctmCandidateTop`
- Code: `CtmCandidateAnalysis`

Reports:

- `build/argus-benchmark/reports/ctm-v3-candidate-analysis-20260617/20260617-184704-fabric-candidate-analysis-1.json`
- `build/argus-benchmark/reports/ctm-v3-candidate-analysis-20260617/20260617-184826-neoforge-candidate-analysis-1.json`

Die Top-Kandidaten sind auf Fabric und NeoForge praktisch gleich. Die teuersten
Sets sind keine gemischten CTM-Regeln, sondern grosse Overlay-only Arrays:

| Loader | Block/Sprite/Face | Calls | Regeln | Profil |
| --- | --- | ---: | ---: | --- |
| Fabric | deepslate / deepslate_top / up | 435122 | 179 | `overlay`, `full_8`, `connect=block` |
| Fabric | grass_block / grass_block_top / up | 367777 | 55 | `overlay`, `full_8`, `connect=block` |
| Fabric | stone / stone / up | 323412 | 190 | `overlay`, `full_8`, `connect=block` |
| NeoForge | deepslate / deepslate_top / up | 414610 | 179 | `overlay`, `full_8`, `connect=block` |
| NeoForge | grass_block / grass_block_top / up | 362545 | 55 | `overlay`, `full_8`, `connect=block` |
| NeoForge | stone / stone / up | 312118 | 190 | `overlay`, `full_8`, `connect=block` |

Alle Top-8 pro Loader haben:

- `methods=overlay:*`
- `connectivity=full_8:*`
- `connectModes=block:*`
- Conditions: `connectTiles:*`, `matchBlocks:*`

Interpretation:

- Das aktuelle Problem ist nicht mehr allgemeiner CTM-Dispatch, sondern
  Overlay-Stapel mit vielen Regeln, die pro Face/Sprite sehr oft durchlaufen.
- Weitere generische Selector-Mikrooptimierungen sind wahrscheinlich
  Snake Oil. Mehrere Varianten wurden bereits gemessen und verworfen.
- Der naechste sinnvolle Schritt ist ein reload-seitig vorbereiteter,
  dedizierter Overlay-Gruppenpfad fuer homogene Kandidatenarrays:
  `overlay + full_8 + connect=block + matchBlocks/connectTiles`.
- Der Gruppenpfad darf nicht wieder ein zusaetzlicher Branch pro Regel sein.
  Er muss Kandidaten schon vor dem Hotpath in kleinere, homogene Gruppen
  sortieren oder vorberechnen.

## Effective Candidate Analysis 2026-06-17

Status: **implementiert und auf beiden Loadern verifiziert.**

Ergaenzung:

- `ctmCandidateSets` enthaelt jetzt neben den rohen `blockRules` auch:
  - `averageEffectiveBlockRules`
  - `maxEffectiveBlockRules`
  - `zeroEffectiveBlockRuleCalls`
- Die Werte werden erst nach dem `CtmNeighborRuleIndex`-Filter erfasst. Damit
  zeigt der Report, wie viele Blockregeln im Resolver wirklich noch
  ausgewertet werden.

Validierter Command:

```powershell
.\gradlew.bat :src:shared:test :src:fabric:build :src:neoforge:build
```

Benchmark-Reports:

- `build/argus-benchmark/reports/ctm-v3-effective-candidate-analysis-20260617/20260617-141718-fabric-effective-candidate-analysis-1.json`
- `build/argus-benchmark/reports/ctm-v3-effective-candidate-analysis-20260617/20260617-141840-neoforge-effective-candidate-analysis-1.json`

Auszug:

| Loader | Block/Sprite/Face | Raw Block Rules | Avg Effective | Max Effective |
| --- | --- | ---: | ---: | ---: |
| Fabric | deepslate / deepslate_top / up | 179 | 0.082 | 3 |
| Fabric | grass_block / grass_block_top / up | 55 | 0.009 | 2 |
| Fabric | stone / stone / up | 190 | 0.245 | 4 |
| NeoForge | deepslate / deepslate_top / up | 179 | 0.082 | 3 |
| NeoForge | grass_block / grass_block_top / up | 55 | 0.009 | 2 |
| NeoForge | stone / stone / up | 190 | 0.245 | 4 |

Interpretation:

- Der Neighbor-Rule-Index reduziert die grossen Overlay-Kandidatenarrays
  bereits auf fast nichts. Die naechste groessere Verbesserung liegt deshalb
  nicht mehr in noch mehr Regel-Loop-Mikrooptimierung.
- `ctm.resolve` ist jetzt vor allem sehr haeufiger Resolver-Aufruf mit wenig
  Restarbeit. Ein bereits getesteter Empty-Filter-Fast-Return half Fabric, war
  aber auf NeoForge schlechter und bleibt deshalb verworfen.
- Der naechste sinnvolle Hebel muss die Anzahl der Resolve-Aufrufe oder die
  Block-/Face-/Sprite-Cache-Trefferquote verbessern, ohne einen neuen Branch
  pro Regel einzufuehren.

## Rejected: Face/Sprite Cache Disable 2026-06-17

Status: **revertiert.**

Experiment:

- Der block-lokale `ArgusCtmFacePlan`-Cache wurde per Dev-Flag deaktivierbar
  gemacht.
- Motivation: Die aktuelle Trefferquote liegt nur bei etwa 2-3 Prozent. Wenn
  der Cache netto mehr kostet als spart, koennte das Entfernen schneller sein.

Benchmark-Reports:

- `build/argus-benchmark/reports/ctm-v3-face-cache-disabled-20260617/20260617-142318-fabric-face-cache-disabled-1.json`
- `build/argus-benchmark/reports/ctm-v3-face-cache-disabled-20260617/20260617-142440-neoforge-face-cache-disabled-1.json`

Vergleich:

| Loader | Zustand | Avg FPS | Median FPS | `sodium.process_quad` total | `sodium.ctm` total | `ctm.resolve` total |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Fabric | Cache aktiv | 1113.890 | 1083 | 22575.6 ms | 13647.9 ms | 7180.2 ms |
| Fabric | Cache deaktiviert | 933.357 | 901 | 22734.7 ms | 13783.2 ms | 7081.3 ms |
| NeoForge | Cache aktiv | 1048.080 | 1035 | 22962.5 ms | 13743.6 ms | 7178.5 ms |
| NeoForge | Cache deaktiviert | 905.724 | 892 | 23253.1 ms | 14189.2 ms | 7267.9 ms |

Interpretation:

- Der Cache bleibt trotz niedriger Trefferquote richtig. Fabric zeigte zwar
  minimal weniger `ctm.resolve`, verlor aber massiv FPS und wurde in
  `sodium.ctm`/`process_quad` schlechter.
- NeoForge wurde in allen relevanten CTM-/Sodium-Buckets schlechter.
- Der Dev-Flag und die Codeaenderung wurden entfernt. Der richtige naechste
  Schritt ist nicht Cache-Entfernung, sondern gezieltere Cache-Treffer oder
  weniger Cache-Misses.

## Accepted: Neighbor Rule Index 2026-06-17

Status: **unit-, build- und benchmark-verifiziert.**

Implementiert:

- `CtmNeighborRuleIndex` baut reload-seitig fuer grosse homogene
  Overlay-Kandidatenarrays einen Block-/Sprite-zu-Regelmasken-Index.
- Aktiv nur fuer konservative Kandidatensaetze:
  `method=overlay|overlay_ctm`, Overlay-only, `connect=block`, keine
  Biome-/Height-Sonderbedingungen, mindestens 32 Regeln.
- `CtmCandidateScratch` filtert Block-Fallback-Regeln zur Laufzeit anhand der
  acht Overlay-Nachbarpositionen in einen wiederverwendeten Array-Scratch.
- Sprite-Regeln laufen unveraendert zuerst; stabile Overlay-Reihenfolge bleibt
  erhalten.
- Regressionstest:
  `CtmRenderResolverTest.resolvePlan_largeOverlayCandidateSetKeepsMatchingRule`.

Validierte Commands:

```powershell
.\gradlew.bat :src:shared:test :src:fabric:build :src:neoforge:build
.\gradlew.bat :src:shared:test --tests com.argus.ctm.CtmRenderResolverTest
```

Benchmark-Reports:

- `build/argus-benchmark/reports/ctm-v3-neighbor-rule-index-20260617/20260617-134004-fabric-neighbor-rule-index-1.json`
- `build/argus-benchmark/reports/ctm-v3-neighbor-rule-index-20260617/20260617-134126-neoforge-neighbor-rule-index-1.json`
- `build/argus-benchmark/reports/ctm-v3-material-sprite-cache-20260617/20260617-140104-fabric-material-sprite-cache-1.json`
- `build/argus-benchmark/reports/ctm-v3-material-sprite-cache-20260617/20260617-140224-neoforge-material-sprite-cache-1.json`

Vergleich gegen die neue TP125-Basis:

| Loader | Zustand | Avg FPS | Median FPS | P05 | P01 | `ctm.resolve` total | `ctm.resolve` avg |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Fabric | TP125-Basis, beste 3er-Spanne | 946.86-978.31 | 888-958 | 719-741 | 639-735 | 110629.6-115001.2 ms | 19175.6-20196.2 ns |
| Fabric | Neighbor Rule Index | 1083.52 | 1060 | 731 | 668 | 8639.1 ms | 1434.0 ns |
| Fabric | Material Sprite Cache | 1126.51 | 1081 | 781 | 727 | 6988.5 ms | 1188.4 ns |
| NeoForge | TP125-Basis, 3er-Spanne | 794.24-908.56 | 838-914 | 32-684 | 30 | 107411.7-131369.3 ms | 18706.8-23099.5 ns |
| NeoForge | Neighbor Rule Index | 1058.80 | 1054 | 760 | 33 | 6929.8 ms | 1152.9 ns |
| NeoForge | Material Sprite Cache | 1061.53 | 1035 | 742 | 32 | 6692.5 ms | 1114.4 ns |

Interpretation:

- Das ist der erste nach der Candidate-Analyse akzeptierte grosse Hebel.
- `ctm.resolve` sinkt auf beiden Loadern um etwa eine Groessenordnung.
- FPS steigen auf beiden Loadern deutlich.
- NeoForge hatte am Run-Ende weiterhin einen allgemeinen Low-FPS-/GC-Stall
  mit 20 Low-FPS-Samples; der CTM-Bucket blieb dabei trotzdem stark reduziert.
  Das ist eher Runtime-/Heap-Stabilitaet als ein `ctm.resolve`-Regression.

## Accepted: Material Sprite Cache 2026-06-17

Status: **build- und benchmark-verifiziert.**

Implementiert:

- `CtmSodiumMaterialSpriteCache` cached pro `CtmSodiumQuadProcessor` die
  Aufloesung von `CtmMaterialEntry` zu `TextureAtlasSprite`.
- Der Cache wird geleert, wenn die immutable `CtmMaterialTable`-Snapshot-
  Referenz wechselt.
- Die Aenderung sitzt in `src/client` und gilt dadurch fuer Fabric und
  NeoForge identisch.

Validierter Command:

```powershell
.\gradlew.bat :src:shared:test :src:fabric:build :src:neoforge:build
```

Benchmark-Vergleich:

| Loader | Zustand | Avg FPS | Median FPS | `sodium.process_quad` total | `sodium.ctm` total | `ctm.resolve` total |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Fabric | Neighbor Rule Index | 1083.52 | 1060 | 27200.3 ms | 16775.6 ms | 8639.1 ms |
| Fabric | Material Sprite Cache | 1126.51 | 1081 | 22135.3 ms | 13498.1 ms | 6988.5 ms |
| NeoForge | Neighbor Rule Index | 1058.80 | 1054 | 22134.8 ms | 13381.8 ms | 6929.8 ms |
| NeoForge | Material Sprite Cache | 1061.53 | 1035 | 21257.2 ms | 12864.4 ms | 6692.5 ms |

Interpretation:

- Der Cache ist kein neuer CTM-Selector-Hebel, senkt aber die
  Material-/Sprite-Realisierungskosten im Sodium-Pfad.
- Fabric profitiert deutlich in `sodium.process_quad`, `sodium.ctm` und
  `ctm.resolve`.
- NeoForge profitiert kleiner, aber in denselben Buckets. Der bekannte
  NeoForge-Endstall blieb ein allgemeines Runtime-/GC-Thema, nicht ein
  CTM-Regression.

Erstversuch-Problem:

- Der erste Fabric-Run crashte wegen eines Scratch-Maskenlängen-Mismatches,
  wenn ein grosser vorheriger Index und ein kleiner aktueller Index denselben
  Maskenpuffer nutzten. Fix: Masken-Merge laeuft ueber die Source-Laenge, nicht
  ueber die wiederverwendete Target-Laenge.

## Rejected: Empty Filter Fast Return 2026-06-17

Status: **revertiert.**

Experiment:

- `CtmRenderResolver.resolveCandidatesInto(...)` brach direkt ab, wenn der
  Neighbor-Index nach dem Nachbarfilter keine Block-Regeln mehr uebrig liess
  und keine Sprite-Regeln vorhanden waren.
- Unit-Test und Build waren gruen.

Benchmark-Vergleich:

| Loader | Zustand | Avg FPS | Median FPS | P05 | P01 | `sodium.ctm` total | `ctm.resolve` total | `ctm.resolve` avg |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Fabric | Neighbor Rule Index | 1083.52 | 1060 | 731 | 668 | 16775.6 ms | 8639.1 ms | 1434.0 ns |
| Fabric | Empty Filter Fast Return | 1141.90 | 1115 | 829 | 707 | 13616.6 ms | 7270.4 ms | 1182.6 ns |
| NeoForge | Neighbor Rule Index | 1058.80 | 1054 | 760 | 33 | 13381.8 ms | 6929.8 ms | 1152.9 ns |
| NeoForge | Empty Filter Fast Return 1 | 1043.17 | 1054 | 728 | 32 | 13646.9 ms | 7229.6 ms | 1201.7 ns |
| NeoForge | Empty Filter Fast Return 2 | 1054.06 | 1053 | 727 | 32 | 13741.6 ms | 7415.2 ms | 1205.6 ns |

Interpretation:

- Fabric profitierte klar, aber NeoForge verlor reproduzierbar in den
  CTM-Buckets.
- Da das Ziel loader-parallel ist und `ctm.resolve` auf NeoForge schlechter
  wurde, bleibt der akzeptierte Neighbor-Rule-Index ohne diesen Fast-Return
  die aktuelle Basis.

## Rejected: Direct Block-ID Match Fastpath 2026-06-17

Status: **revertiert.**

Experiment:

- `CtmSelector` verglich bereits namespaced Minecraft-Block-IDs zuerst direkt
  gegen `matchBlocks`, `connectBlocks` und `connectTiles`-Fallback-IDs.
- Die alte Normalisierung per `indexOf(':')` sollte nur noch fuer legacy bare
  IDs laufen.
- Unit-Tests und beide Loader-Builds waren gruen.

Benchmark-Vergleich:

| Loader | Zustand | Avg FPS | Median FPS | P05 | `sodium.process_quad` total | `sodium.ctm` total | `ctm.resolve` total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Fabric | Material Sprite Cache | 1126.51 | 1081 | 781 | 22135.3 ms | 13498.1 ms | 6988.5 ms |
| Fabric | Direct Block-ID Fastpath | 1115.51 | 1087 | 695 | 23295.6 ms | 13840.5 ms | 7593.8 ms |
| NeoForge | Material Sprite Cache | 1061.53 | 1035 | 742 | 21257.2 ms | 12864.4 ms | 6692.5 ms |
| NeoForge | Direct Block-ID Fastpath | 1014.26 | 1012 | 650 | 22274.3 ms | 13320.9 ms | 6820.5 ms |

Interpretation:

- Der zusaetzliche zweistufige Vergleich war auf beiden Loadern schlechter.
- Wahrscheinlich ist der bisherige einfache Normalisierungs- und
  Einzelschleifenpfad fuer JIT und die realen kurzen Blocklisten guenstiger.
- Der Code wurde revertiert; Material Sprite Cache bleibt die aktuelle Basis.
