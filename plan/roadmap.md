# Cinder Roadmap - Sodium-first OptiFine Parity

Stand: 2026-06-14

Diese Datei ist ab jetzt die operative Planungsquelle fuer Cinder. Aeltere
Phasen- und Bring-up-Dokumente liegen unter `plan/archive/2026-06-legacy/`.
`plan/cinder-vergleich.md` bleibt als strategische Vergleichsnotiz erhalten,
ist aber keine Statusquelle.

## Leitplanken

- Sodium ist Requirement und der primaere Renderer-Pfad.
- Fabric ist der aktive Loader; NeoForge bleibt geplant, aber bis zu einer
  stabilen 26.2-Toolchain nachrangig.
- OptiFine und Continuity duerfen nur als Verhaltensreferenz dienen. Kein Code,
  keine Tabellenstruktur und kein Kontrollfluss werden kopiert.
- `src/shared` bleibt loader-agnostisch. Sodium-, Fabric-, Minecraft-Client-
  und Mixin-Code gehoert in Loader-/Client-Adapter.
- Performance ist Feature-Bestandteil. Hotpaths vermeiden Streams, Lambdas,
  Boxing, wiederholte Identifier-Objekte und ungated Logs.
- Sichtbare Features gelten erst als "Complete", wenn Unit-/Fixture-Tests und
  Ingame-Smoke bestanden sind.

## Statusbegriffe

- **Planned:** Ziel ist beschrieben, Code fehlt.
- **Implemented:** Code existiert und kompiliert.
- **Unit-verified:** relevante Unit-Tests bestehen.
- **Fixture-verified:** echte Resource-Pack-Fixtures bestehen.
- **Ingame-verified:** ein echter Client rendert die erwarteten Bilder.
- **Complete:** alle fuer das Feature relevanten Pruefungen sind erledigt.

## Aktueller Stand

### Renderer

- Sodium/Vulkan startet im Dev-Client.
- Vanilla-/Mojang-Terrain-CTM-Mixins sind nicht mehr der Produktivpfad.
- Aktiver Hook: Sodium `BlockRenderer.processQuad(...)`.
- Cinder hat einen eigenen Eintrag im Sodium-Settings-Menue.
- CTM hat einen eigenen Sodium-Settings-Tab.
- Better Grass hat einen eigenen Sodium-Settings-Tab.

### CTM

- OptiFine/Continuity-kompatible CTM-Properties werden geladen.
- CTM-Materialtabelle und Sprite-Injection funktionieren mit PureBDcraft Base
  + CTM Transitions.
- Replacement-CTM laeuft ueber Sodium-Quad-Remap.
- Overlay-CTM emittiert Zusatzquads durch Sodiums normale Tint-/AO-/Lighting-
  Pipeline.
- Glass/Iron/Gold/Bookshelf/Overlays wurden mehrfach ingame korrigiert, sind
  aber noch nicht als vollstaendige Paritaets-Suite abgesichert.
- Debug-Logging ist config-gated.

### Better Grass

- Config: `cinder.better_grass.mode = off|fast|fancy`.
- Fast: Grasblock-Seiten verwenden die Grass-Top-Textur.
- Fancy: Grasblock-Seiten verwenden die Grass-Top-Textur nur, wenn in
  Face-Richtung unten daneben ebenfalls ein Grasblock liegt.
- Weitere Blockfamilien sind implementiert und per Sodium-Menue toggelbar:
  `grass_block`, `snowy_grass_block`, `dirt_path`, `farmland`, `mycelium`,
  `podzol`, `crimson_nylium`, `warped_nylium`.
- Snowy-Varianten von `grass_block`, `mycelium` und `podzol` verwenden die
  Snow-Textur ohne Tint und sind ueber einen gemeinsamen Snowy-Toggle
  steuerbar.
- Fancy nutzt fuer alle Blockfamilien dieselbe Fortsetzungsregel: In
  Face-Richtung unten daneben muss dieselbe Blockfamilie liegen.
- Bei abgesenkten Blockfamilien (`dirt_path`, `farmland`) uebernimmt ein
  darunterliegender Dirt-Block die seitliche Top-Textur, wenn der Block darueber
  auf dieser Seite Better Grass bekommen wuerde.
- Noch offen: vollstaendige Snow-Handling-Paritaet fuer
  `bettergrass.properties`, `grass.multilayer`.

## Phase A - CTM Stabilisierung

Ziel: CTM als belastbares Fundament stabilisieren, bevor neue Featurefamilien
auf denselben Renderpfad aufbauen.

### A1. Vorfilter-Cache

Status: Complete

Problem:

- Irrelevante Quads sollen vor Selector-/Neighbour-Arbeit abbrechen.
- Der Sodium-Hotpath soll keine wiederholten Sprite-Identifier-Objekte fuer
  bereits bekannte Atlas-Sprites erzeugen.

Implementiert:

- `CtmRuleSet` baut einen immutable `CtmRenderIndex` mit Sprite-/Block- und
  Face-Kandidaten.
- `CtmRenderResolver` fragt den Index vor Selector-Arbeit ab.
- `CtmSodiumQuadProcessor` nutzt den Vorfilter vor `NeighborView`-Aufbau und
  cached Sprite-`NamespaceId`s pro Processor.

Verifikation:

- Shared Unit-Tests fuer Snapshot-Aufbau und Prefilter-Skip.
- Fabric Build.
- Ingame-Smoke: keine sichtbare Regression bei Glass, Iron, Gold und Overlays
  bemerkt.

### A2. Expliziter Multipass-Resolver

Status: Complete

Problem:

- Replacement und Overlay existierten bereits, aber der Resolver-Output war
  noch nicht sauber als "1 Replacement + N Overlays" modelliert.
- Spaetere Features wie Emissive brauchen dieselbe Trennung.

Implementiert:

- Shared Output-Typ `CtmRenderPlan`:
  - optionales Replacement-Ergebnis.
  - immutable Overlay-Liste.
  - `hasWork`, `hasReplacement`, `hasOverlays`.
- `CtmRenderResolver.resolvePlan(...)` ist die neue Plan-API.
- `resolve(...)` bleibt als Kompatibilitaetswrapper fuer bestehende Commands.
- Sodium konsumiert den Plan und kann Replacement plus Overlays im selben Quad
  realisieren.
- Overlay-Reihenfolge bleibt Resource-Pack-/Prioritaetsvertrag.

Verifikation:

- Tests fuer mehrere Overlay-Regeln auf demselben Quad.
- Tests fuer Replacement ohne Overlay, Overlay ohne Replacement und Overlay vor
  Replacement.
- Ingame-Smoke: keine sichtbare Regression bei Grass/Sand/Gravel/Snow,
  Gold und Glass bemerkt.

### A3. CTM Oracle und Fixture-Suite ausbauen

Status: Complete

Implementiert:

- 47-Tile-Oracle fuer konkrete Side-/Diagonal-Masks erweitert.
- Resolver-Plan-/Prefilter-Tests erweitert.

- Command-Ausgaben fuer CTM/Overlay-Logging behalten, aber Debug-Flag-gated.

Verifikation:

- `.\gradlew.bat :src:shared:test`
- `.\gradlew.bat :src:fabric:build`
- `.\gradlew.bat :src:fabric:runClient`

Ingame-Smoke-Matrix fuer Phase A:

- Normal Glass 3x3 Wand.
- Stained/Tinted Glass mit expliziten 47 PNGs.
- Iron/Gold 3x3, Plus und O-Shape.
- Bookshelves horizontal.
- Sandstone/top-style rules.
- Grass/Sand/Gravel/Snow Overlays inklusive Diagonalen.

Ergebnis: Keine sichtbare Regression im Dev-Client bemerkt; Phase A ist damit
fuer den aktuellen Stabilisierungsscope abgeschlossen.

## Phase B - Better Grass vollstaendig machen

Ziel: OptiFine-kompatibles Better Grass Verhalten mit Sodium-native Quad-Remap,
ohne OptiFine-Modellreplacement und ohne Verlust von AO/Lighting.

### B1. Snow-Handling

Status: Unit-verified

Implementiert:

- Grass/Mycelium/Podzol mit `snowy=true` oder `SNOW`/`SNOW_BLOCK` direkt
  darueber remappen Seiten auf die Snow-Textur.
- Fast: immer fuer relevante Seiten.
- Fancy: nur wenn in Face-Richtung die gleiche Snow-/Nicht-Snow-Variante
  anschliesst.
- Snow-Textur nicht grass-tinten.

Verifikation:

- Ingame: Schnee auf Gras bleibt weiss/hell korrekt, nicht gruen.
- Fast/Fancy/Off im Sodium-Menue umschaltbar.

### B2. Weitere Blocktypen

Status: Unit-verified

Abdeckung:

- `grass_block`
- `dirt_path`
- `farmland`
- `mycelium`
- `podzol`
- `crimson_nylium`
- `warped_nylium`

Plan:

- Kleiner Blocktyp-Table im Sodium-Adapter ist vorhanden.
- Pro Typ werden Zieltextur, optionaler Snow-Zieltyp, Tint-Policy und
  Fancy-Neighbour-Policy aus `BetterGrassRules` gelesen.
- Per-Blockfamilie Sodium-Settings-Toggle und persistierte Config-Keys bleiben
  der Fallback ohne Resource-Pack.
- Keine HashMap im Hotpath; die festen Vanilla-Typen laufen ueber eine kleine
  statische Tabelle.

Verifikation:

- `.\gradlew.bat :src:shared:test`
- `.\gradlew.bat :src:fabric:compileClientJava :src:fabric:compileJava --rerun-tasks`
- Offen: Ingame-Smoke fuer alle sieben Blockfamilien in Fast/Fancy/Off.

### B3. `bettergrass.properties`

Status: Unit-verified

Implementiert:

- Parser fuer `assets/minecraft/optifine/bettergrass.properties`.
- Fabric-Reload-Listener liest die aktive `minecraft:optifine/bettergrass.properties`
  und veroeffentlicht einen immutable `BetterGrassRules` Snapshot.
- Pack-Datei gewinnt gegen per-Familie Sodium-Menue-Toggles; globales
  Off/Fast/Fancy bleibt runtime-aktiv.
- Unterstuetzte Keys:
  - `grass`, `dirt_path`, `farmland`, `mycelium`, `podzol`,
    `crimson_nylium`, `warped_nylium`
  - `grass.snow`, `mycelium.snow`, `podzol.snow`
  - `texture.grass`, `texture.grass_side`, `texture.dirt_path`,
    `texture.dirt_path_side`, `texture.farmland`, `texture.farmland_side`,
    `texture.mycelium`, `texture.podzol`, `texture.crimson_nylium`,
    `texture.warped_nylium`, `texture.snow`
- `grass.multilayer` wird geparst. Renderer-MVP faellt aktuell debug-gated auf
  normalen Replacement-Modus zurueck, bis getintete Zusatzquads stabil
  realisiert sind.

Verifikation:

- Parser-Tests in `src/shared`.
- Fabric Build.
- Offen: Ingame mit PureBDcraft und Vanilla.

## Phase C - Emissive MVP

Ziel: OptiFine-kompatible Emissive Textures sichtbar machen, zuerst ohne Bloom.

Status: Implemented

Implementiert:

- Shared Parser fuer `assets/minecraft/optifine/emissive.properties`.
- `suffix.emissive` wird unterstuetzt; Default ist `_e`.
- Fabric-Reload baut einen immutable `EmissiveSpriteTable` Snapshot:
  `baseSprite -> emissiveSprite`.
- Die Blockatlas-SpriteSource injiziert erkannte Emissive-Pendants in den
  Blockatlas, auch wenn sie nicht in Vanilla-/Pack-Modellen referenziert sind.
- Sodium-Hotpath:
  - Basis-Quad bleibt normal.
  - Wenn der aktuelle Sprite ein Emissive-Pendant hat, wird ein Zusatzquad mit
    Emissive-Sprite emittiert.
  - Zusatzquad nutzt Fullbright-Light.
- Kein globaler Render-State; Renderer liest nur immutable Snapshots.

Nicht-Ziel im MVP:

- Bloom/MRT.
- tiefer Shader-Umbau.
- Emissive fuer CTM-Replacement-Sprites ist noch nicht Teil des MVP; normale
  Atlas-Sprites und Better-Grass/Vanilla-Quads sind der erste Zielpfad.

Verifikation:

- `.\gradlew.bat :src:shared:test`
- `.\gradlew.bat :src:fabric:build`
- Offen: Fixture-Pack mit `_e`-Texturen.
- Offen: Ingame: Emissive bleibt hell in dunkler Umgebung.
- Offen: Keine CTM/Overlay-Regression.

## Phase D - Gemeinsames Condition-System

Ziel: Eine gemeinsame Bedingungs-Engine fuer CIT, Custom GUI und spaetere
Featurefamilien.

Status: Unit-verified

Implementiert:

- Shared Condition-Modell in `src/shared`:
  - `Condition`
  - `ConditionSet`
  - `ConditionContext`
  - `ConditionKey`
  - `ConditionCost`
- Kostenklassen:
  - O(1): Item/Block/Screen-ID.
  - billig: Damage, StackSize, Enchant-Hash, Component-Existenz.
  - teuer: Regex, NBT/Component-Traversal.
- Bedingungen werden beim `ConditionSet`-Build nach Kosten sortiert und bei
  der ersten nicht passenden Condition kurzgeschlossen.
- `ComponentMatchers` bleibt der Syntax-Compiler fuer `pattern`, `ipattern`,
  `regex`, `iregex`, `range`, `exists`, `raw` und Negation, kann nun aber
  Kosten und Missing-Semantik fuer `exists:false` ausdruecken.
- `ConditionPropertiesReader` liest gemeinsame CIT-/Custom-GUI-Condition-Keys
  aus `PropertiesFile`, ohne ein sichtbares Feature zu aktivieren.
- Malformed Conditions werfen `ConditionParseException` mit Key und
  Originalwert fuer spaetere Reload-Fehlerisolation.
- Keine loader-spezifischen Typen in `src/shared`; Fabric-Adapter fuer echte
  `ItemStack`-, Screen-, BlockEntity- und Biome-Daten folgen in Phase E/F.

Verifikation:

- `.\gradlew.bat :src:shared:test`
- Unit-Tests fuer pattern/regex/range/existence inklusive Missing Values.
- Unit-Tests fuer Kosten-Sortierung und Kurzschlussverhalten.
- Unit-Tests fuer CIT-Key-Parsing und Custom-GUI-Key-Parsing.
- Keine Ingame-Verifikation noetig; Phase D ist Foundation-only.

## Phase E - CIT

Ziel: OptiFine-kompatible Custom Item Textures mit schnellem Lookup.

Status: Partially ingame-verified

Implemented:

- Shared `com.cinder.cit` Modell fuer `type=item` Regeln:
  `CitRule`, `CitRuleSet`, `CitRuleType`, `CitReplacement`,
  `CitParseResult`, `CitRuleParser`.
- `optifine/cit/**/*.properties` Parsing fuer Items, Weight,
  Texturen/Modelle, Damage, DamageMask, DamagePercent, StackSize,
  Enchantments, Hand, `components.*` und legacy `nbt.*`.
- Bedingungen laufen ueber `ConditionSet` und `ConditionContext`.
- Fabric Client-Snapshot mit `IdentityHashMap<Item, CitRule[]>`.
- Runtime-Cache ist content-basiert: Item, Count, Damage, MaxDamage,
  Enchant-Hash, Component-Hash und Hand. Kein
  `System.currentTimeMillis()` im Hotpath.
- CIT Texturen werden ueber eine Item-Atlas SpriteSource geladen.
- ItemModelResolver-Hook:
  - `model=` Regeln koennen auf ein gebackenes Replacement-ItemModel
    umbiegen.
  - `texture=` Regeln remappen vanilla erzeugte Item-Quads auf die
    Replacement-Sprite.
- Globaler Config-Schalter `cinder.cit.enabled`, Sodium-Menue Seite `CIT`.
- Wenn CIT Resewn geladen ist, bleibt Cinder CIT runtime-seitig fail-safe aus.

Verifikation:

- `.\gradlew.bat :src:shared:test` gruen am 2026-06-14.
- `.\gradlew.bat :src:fabric:build` gruen am 2026-06-14.
- Fixture-Pack mit Damage-, Enchant- und Name-Regeln.
- Ingame Items im Inventar, Hand, Item Frame.
- Erst Complete, wenn Ingame-Smoke diese Szenen bestaetigt.

## Phase F - Custom GUI

Ziel: OptiFine-kompatible GUI-Texture-Replacements.

Status: Unit/build-verified

Implemented:

- Shared `com.cinder.customgui` Modell:
  `CustomGuiRule`, `CustomGuiRuleSet`, `CustomGuiReplacement`,
  `CustomGuiParseResult`, `CustomGuiRuleParser`.
- `optifine/gui/container/**/*.properties` Parsing fuer `container`,
  `texture`, `texture.<name>`, `weight` und GUI-Conditions aus
  `ConditionPropertiesReader`.
- Fabric Reload-Listener baut immutable Snapshots und isoliert malformed
  Properties-Dateien pro Datei.
- Beim `Gui#setScreen(...)` wird einmalig der Screen klassifiziert und die
  passende Regel gegen `ConditionSet` evaluiert.
- Der Render-Hook ersetzt im `GuiGraphicsExtractor` direct-texture Blit nur
  noch `Identifier -> Identifier` ueber eine immutable Override-Tabelle.
- Globaler Config-Schalter `cinder.custom_gui.enabled`, Sodium-Menue Seite
  `Custom GUI`.
- Wenn OptiGUI geladen ist, bleibt Cinder Custom GUI runtime-seitig fail-safe
  aus.
- Shulker-Box-Farben werden beim Block-Use als kurzlebiger Screen-Context
  erfasst, damit `colors=` Regeln aus Resource-Packs wie PureBDcraft pro Farbe
  matchen koennen.

Aktuelle Screen-Fakten:

- Screen-ID und GUI-Name.
- Chest `large` und `levels` aus `ChestMenu#getRowCount()`.
- Shulker `colors` fuer platzierte Vanilla-Shulkerboxen.
- Biome/Height/BlockEntity-Sonderdaten sind missing-safe, aber noch nicht an
  echte Client-Level-/BlockEntity-Abfragen angebunden.

Noch offen:

- OptiGUI-Syntax-Support.
- Vollstaendige Ingame-Matrix fuer alle unterstuetzten Vanilla-Container.
- GUI-Fixture-Suite mit echten Resource-Pack-Beispielen.
- BlockEntity-/Level-Fakten fuer container-spezifische Conditions.
- Text-Styling.
- tiefe ScreenNBT-Feature-Paritaet.

Verifikation:

- `.\gradlew.bat :src:shared:test` gruen am 2026-06-14.
- `.\gradlew.bat :src:fabric:build` gruen am 2026-06-14.
- Ingame: PureBDcraft Shulker-Box GUI-Farben greifen pro Farbe.
- Offen: breiter Ingame-Smoke mit Vanilla-Screens und Custom-GUI-
  Resource-Pack; Phase F ist noch nicht Complete.

## Phase G - Custom Animations

Ziel: OF-kompatible Animationslogik, aber mit Vulkan/Sodium-freundlichem
Upload-Konzept.

Status: Research

Plan:

- Erst Minecraft/Sodium Texture-Upload-Pfad fuer 26.2 verstehen.
- Sichtbarkeits-/Usage-BitSet beim Meshing befuellen.
- Nur sichtbare animierte Texturen ticken.
- MVP: partial region upload, CPU-Interpolation vermeiden wenn moeglich.
- Spaeter: GPU-Interpolation/Compute, falls der Backend-Zugriff stabil ist.

Risiko:

- Das kann schnell zu Renderer-Forschung werden. Erst nach CTM/BetterGrass/
  Emissive angehen.

## Phase H - Custom Colors und Sky

Status: Research

Custom Colors:

- OF-/Colormatic-Format parsen.
- Zuerst CPU-korrekt und gecached.
- Danach GPU-Colormap-Sampling pruefen.

Custom Sky:

- OF-Properties-Format.
- Nuit-artige Smooth-Transitions als Cinder-eigene Umsetzung.
- Vulkan/Sodium Sky-Pass erst nach Renderer-Klaerung.

## Operative Reihenfolge

1. CTM Vorfilter-Cache.
2. Expliziter CTM Multipass-Resolver.
3. BetterGrass Snow-Handling.
4. BetterGrass weitere Blocktypen.
5. BetterGrass Properties.
6. Emissive MVP.
7. Gemeinsames Condition-System.
8. CIT.
9. Custom GUI.
10. Animations.
11. Custom Colors.
12. Custom Sky.

## Laufende Verifikation

Nach shared/parser/selector Aenderungen:

```powershell
.\gradlew.bat :src:shared:test
```

Nach Fabric/Sodium/Mixin/Resource Aenderungen:

```powershell
.\gradlew.bat :src:fabric:build
```

Nach sichtbaren Renderer-Aenderungen:

```powershell
.\gradlew.bat :src:fabric:runClient
```

Ingame-Smoke-Szene:

- PureBDcraft Base + CTM Transitions geladen.
- Glass 3x3 Wand von Vorder- und Rueckseite.
- Iron/Gold 3x3, Plus, O-Shape.
- Bookshelves.
- Grass/Sand/Gravel/Snow Overlays mit Diagonalen und Farbtint.
- BetterGrass Off/Fast/Fancy.

## Plan-Dateien

Aktiv:

- `plan/roadmap.md` - operative Source of Truth.
- `plan/cinder-vergleich.md` - Vergleichs- und Architekturideen.
- `plan/README.md` - Plan-Index.

Archiviert:

- Alte Phasen-, Recovery-, Zwischenstand- und Mojang-Sidecar-Notizen liegen
  unter `plan/archive/2026-06-legacy/`.
