# UI Research: T9 APK Tools

Research-only document. No application code, view IDs, copy, or Java behavior were changed.
All measurements below are taken from the committed XML/resources; contrast ratios are computed
with the WCAG relative-luminance formula. No on-device testing is claimed.

## 0. Scope and method

Files inspected:

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_setup.xml`
- `app/src/main/res/values/styles.xml`, `colors.xml`, `strings.xml`
- `app/src/main/res/drawable/*` (card, hero, chip, button, pill, app backgrounds)
- `app/src/main/java/dev/t9/apktools/MainActivity.java`, `SetupActivity.java`, `TimerRingView.java`
- `app/build.gradle`, `AndroidManifest.xml`

Constraints honored throughout: keep the dark-lime identity, keep every string and view ID,
no new libraries, no custom fonts, no Compose, no Java behavior changes. Visual-only Java
tweaks are listed separately in P2 and are optional.

## 1. Reference set (12)

### Documentation (6)

1. **Material Design 3 — Type scale tokens**
   https://m3.material.io/styles/typography/type-scale-tokens
   Borrow: the 5-step scale we are missing. Label Small 11sp, Body Small 12sp, Body Medium 14sp,
   Body Large 16sp, Title Large 22sp, with the documented letter-spacing values (0.5sp on labels,
   not 0.14em). Current code uses 9-10sp labels with 0.14em tracking, which is 3x the M3 value.

2. **Material Design 3 — Color roles (dark)**
   https://m3.material.io/styles/color/roles
   Borrow: surface-container lowest/low/container/high/highest roles and outline vs outline-variant.
   The app currently defines only `surface` and `surface_2`, so MaterialSwitch/RadioButton fallback
   to the purple-tinted baseline neutrals. Add the green-tinted container roles to `Theme.T9`.

3. **Material Design 3 — State layers**
   https://m3.material.io/foundations/interaction/states/state-layers
   Borrow: hover 8%, focus 10%, pressed 10%, dragged 16%, disabled 38% content / 12% container.
   Current ripples are 15-20% lime; primary-button ripple is nearly invisible on a lime fill.

4. **Material Design 3 — Motion tokens**
   https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
   Borrow: 150-400ms for state/layout changes, emphasized-decelerate for exits. The ring's 650ms
   decelerate is slower than the M3 medium band and makes the hero feel laggy.

5. **Android accessibility — touch targets and contrast**
   https://support.google.com/accessibility/android/answer/7101858?hl=en
   Borrow: 48x48dp minimum touch targets separated by 8dp, 4.5:1 contrast for text under 18sp,
   `android:minWidth`/`minHeight` to expand targets without growing visuals.

6. **Android — Edge-to-edge**
   https://developer.android.com/develop/ui/views/layout/edge-to-edge
   Borrow: `WindowCompat.enableEdgeToEdge`, `ViewCompat.setOnApplyWindowInsetsListener`,
   `clipToPadding="false"` on scrolling content. Target SDK is 34, so enforcement is not yet
   mandatory; keep this as P2.

### Open-source apps (6)

7. **Material Files** — https://github.com/zhanghai/MaterialFiles
   `app/src/main/res/layout/file_item_list.xml`, `values/dimens.xml`
   Borrow: 72dp two-line rows, 48dp touch targets, 16dp screen margin, 12dp icon padding,
   `?selectableItemBackground` as the row foreground, `textAppearanceListItem`. Directly fixes
   our 32-40dp controls and 18/20dp inconsistent gutters.

8. **Shizuku** — https://github.com/RikkaApps/Shizuku
   `manager/src/main/res/layout/home_server_status.xml`, `adb_pairing_tutorial_activity.xml`,
   `values/styles.xml`
   Borrow: icon + title (16sp) + summary (12sp, 4sp gap) status rows, 8dp card gaps, 24/32dp
   step spacing, error-container cards for warnings, numbered 32dp step icons. Maps to our
   Setup permission rows and the ADB command block.

9. **Breezy Weather** — https://github.com/breezy-weather/breezy-weather
   `app/src/main/res/layout/container_main_header.xml`
   Borrow: dominant number centered with a separate smaller unit, `includeFontPadding="false"`,
   `autoSizeTextType="uniform"` (50-130sp), `importantForAccessibility="no"` on the giant number,
   condition line above and "feels like" below. Direct template for `TimerRingView` + `tvTime`.

10. **Aegis** — https://github.com/beemdevelopment/Aegis
    `app/src/main/res/layout/card_entry_compact.xml`
    Borrow: 26sp `sans-serif-light` code with -0.01 tracking, 13sp issuer/account labels,
    3dp bottom progress bar, 0dp elevation cards, 45dp icons, 8/12dp padding. Maps to our IP value
    and the ring's thin progress stroke.

11. **Obtainium** — https://github.com/ImranR98/Obtainium
    `lib/pages/settings.dart`, `lib/theme.dart`
    Borrow: 48dp minimum buttons, 0dp-elevation tonal cards (`surfaceContainerLow`), stadium chips,
    grouped sections with destructive actions isolated at the bottom, fade-forward transitions.
    Pattern only (Flutter); do not copy code.

12. **SD Maid SE** — https://github.com/d4rken-org/sdmaid-se
    `app/.../dashboard/bottom/DashboardHeroCard.kt`
    Borrow: state-colored hero container (error / tertiary / neutral instead of one brand color),
    one primary action, a result-age line, discard separated, explicit focus ladder. Pattern only
    (Compose); do not copy code. Directly supports our "RESET NOW" destructive separation.

## 2. Audit of the current layouts

### 2.1 Hierarchy

- `activity_main.xml:64-211` (hero card) contains the label, status pill, 250dp ring, 6 preset
  chips, `tvNext`, the primary button, and the reset/repeat row — 8 interactive elements in one
  surface. The primary action (`btnPrimary`, `activity_main.xml:160-164`) sits below the 250dp ring
  and the chip row, so on a 640dp-tall screen it is below the fold.
- `activity_main.xml:213-252`: the two status chips are siblings of the hero card, not inside it,
  so their relationship to the timer is ambiguous; they read as floating pills.
- `activity_main.xml:254-303` and `305-340`: IP and Activity are two identically styled cards
  (`T9Card`), so nothing tells the eye which is primary after the hero.
- `activity_setup.xml:583-612`: the "DANGER ZONE" card uses the same `T9Card` chrome as the
  permission and timing cards; only the button text is red. Destructive actions are not separated
  by spacing or container tone.
- `activity_setup.xml:218-285`: the strategy card mixes a 14sp question, four 46dp radios and a
  11sp helper with no visual grouping beyond dividers.

### 2.2 Spacing rhythm

Values are not on the 4/8dp grid in several places:

| Location | Current | Issue |
|---|---|---|
| `styles.xml:44` T9Card padding | 18dp | not 4-grid (should be 16 or 20) |
| `styles.xml:45` T9Card marginTop | 14dp | not 8-grid |
| `styles.xml:53-54` T9CardHero padding/margin | 18/18dp | inconsistent with T9Card |
| `activity_main.xml:14-17` root padding | 20/18dp | top not 8-grid |
| `activity_main.xml:131` chips row marginTop | 6dp | breaks 8-grid |
| `activity_main.xml:154` tvNext marginTop | 10dp | breaks 8-grid |
| `activity_main.xml:163` btnPrimary marginTop | 12dp | ok, but hero-internal gaps vary 4/6/10/12 |
| `activity_main.xml:169` reset row marginTop | 10dp | breaks 8-grid |
| `activity_main.xml:346` footer marginTop | 22dp | not 8-grid |
| `styles.xml:157-158` T9Divider margins | 2dp/2dp | adds 5dp total, uneven row gaps |
| `activity_setup.xml:14-17` root padding | 20/16dp | top ok, but differs from main (18) |
| `activity_setup.xml:236` RadioGroup marginTop | 4dp | breaks 8-grid |

### 2.3 Touch targets

| Control | File:line | Size | Status |
|---|---|---|---|
| T9Chip | `styles.xml:69` | 38dp | below 48 |
| T9SmallGhost (SETUP/CLEAR/OPEN/COPY) | `styles.xml:131` | 34dp | below 48 |
| T9StatusPill (interactive? no) | `styles.xml:116` | wrap + 4dp pad | ~20dp, non-interactive, ok but small |
| btnRefreshIp | `activity_main.xml:274-275` | 38dp | below 48 |
| chipAccess / chipDirect | `activity_main.xml:222,241` | 40dp | below 48 |
| btnBack | `activity_setup.xml:27-28` | 40dp | below 48 |
| RadioButton x4 | `activity_setup.xml:242,251,260,269` | 46dp | below 48 |
| btnClearLog | `activity_main.xml:321` | 32dp | below 48 |
| tvFooter | `activity_main.xml:351` | 8dp padding, ~30dp total | below 48 |
| switchRepeat container | `activity_main.xml:186` | 52dp | ok, but only the switch itself is tappable |
| SeekBar | `activity_setup.xml:318-327` | wrap | thumb ~20dp; bar is full-width, acceptable |

### 2.4 Contrast (computed)

| Pair | Ratio | Verdict |
|---|---|---|
| `text` on `surface` | 16.29:1 | pass |
| `muted` on `surface` | 6.79:1 | pass |
| `muted` on `surface_2` | 6.21:1 | pass |
| `muted_dim` on `surface` | 3.67:1 | **fail** for 9-10sp text |
| `muted_dim` on `surface_2` | 3.36:1 | **fail** |
| `muted_dim` on `bg` | 3.98:1 | **fail** for small text |
| `lime` on `surface` | 15.29:1 | pass |
| `danger` on `surface` | 6.50:1 | pass |
| `warn` on `surface` | 11.73:1 | pass |
| `ok` on `bg_pill_ok` | 12.95:1 | pass |
| `stroke` on `surface` | 1.31:1 | decorative only; **fails** 3:1 non-text boundary |
| `surface_2` on hero end `#0E130C` | 1.14:1 | **fails** 3:1 non-text boundary |
| `stroke_bright` on `surface` | 1.75:1 | still below 3:1 |

`muted_dim` is used for helper text at 9-10sp in at least five places
(`activity_main.xml:301,362`; `activity_setup.xml:333,460,596`), so this is a real
accessibility failure, not a polish issue.

### 2.5 State feedback

- `bg_chip.xml` is a `selector` only: selected fill vs unselected fill + stroke. There is no
  `state_pressed`/ripple, so pressing a preset chip gives no feedback until selection resolves.
- `bg_pill.xml` (used by `chipAccess`, `chipDirect`, the repeat container and status pills) is a
  plain shape with no ripple, even though `chipAccess`/`chipDirect` are clickable navigation
  actions.
- `bg_btn_primary.xml` ripple color `#33C8FF2E` (20% lime) over a lime gradient is almost
  invisible. `bg_btn_ghost.xml` uses `#26C8FF2E` (15% lime), visible but subtle.
- Radio buttons use `android:buttonTint="@color/lime"` (`activity_setup.xml:243,252,261,270`):
  a single color applies to checked and unchecked states, so every radio circle is lime and the
  selected one is not visually distinguished by color.
- Switches are `androidx.appcompat.widget.SwitchCompat` (`activity_main.xml:204`,
  `activity_setup.xml:366,403,439`). Under `Theme.Material3` they render the AppCompat (Material 2)
  switch, not the Material 3 switch, and their unchecked track falls back to baseline neutrals.
- `btnPrimary` and `btnResetNow` disabled state is communicated only by `setAlpha(0.55f)` in
  `MainActivity.java:391-394`; the background stays lime, which is acceptable but should be paired
  with `setEnabled` (already done) and a disabled color role.
- `tvFooter` (`activity_main.xml:342-354`) is clickable with `android:padding="8dp"` and no
  background, so it has no pressed state and a ~30dp target.

### 2.6 Alignment

- Screen gutters are 20dp (`activity_main.xml:14-15`, `activity_setup.xml:14-15`) but card padding
  is 18dp (`styles.xml:44,53`), so card content starts 38dp from the screen edge while list-like
  rows in Setup start 36dp + 12dp = 48dp. The two grids never line up.
- `T9Label` uses `layout_weight="1"` with `wrap_content` width in several headers
  (`activity_main.xml:73-75,263-265,313-315`; `activity_setup.xml:453`); lint prefers 0dp, and at
  large font scale the weight calculation delays layout.
- Header logos differ: 46dp on main (`activity_main.xml:26-27`) vs 38dp on setup
  (`activity_setup.xml:59-60`); titles 20sp vs 19sp.
- `tvIp` is 28sp monospace left-aligned inside a 20dp-padded card; an IPv6 address (39 chars)
  at 28sp is wider than the card and will wrap or clip.
- `tvTime` is 54sp centered in a fixed 250dp box with no auto-size; at font scale 2.0 it is
  108sp and cannot fit.

### 2.7 Text scaling

Fixed heights on text-bearing controls will clip at large font scales:

- `T9Chip` 38dp (`styles.xml:69`), `T9SmallGhost` 34dp (`styles.xml:131`), `T9PrimaryButton` 56dp,
  `T9GhostButton` 48dp, `RadioButton` 46dp (`activity_setup.xml:242,251,260,269`),
  `chipAccess`/`chipDirect` 40dp (`activity_main.xml:222,241`), `btnClearLog` 32dp
  (`activity_main.xml:321`), `tvTime` box 250dp (`activity_main.xml:85`).
- `activity_setup.xml:229` question text and 13sp row labels have no `maxLines`, so a 2.0 scale
  pushes them to two lines; the fixed 46dp radio height then clips the second line.
- `activity_main.xml:229-230` and `248-249`: `singleLine="true"` + `ellipsize="end"` on the 40dp
  status chips means "ACCESSIBILITY OFF" becomes "ACCESSIBIL…" at large scale instead of wrapping.

### 2.8 Overdraw and rendering

- `bg_app.xml` draws a solid `@color/bg` plus a radial lime gradient (`#1FC8FF2E`, radius 520dp)
  covering the full screen. The window background is also `@drawable/bg_app`
  (`styles.xml:19`), and the root `NestedScrollView` sets `android:background="@drawable/bg_app"`
  (`activity_main.xml:6`, `activity_setup.xml:6`): the gradient is rasterized twice per frame.
- The hero card (`bg_card_hero.xml`) is a vertical gradient `#1A2314`→`#0E130C`; inside it,
  `TimerRingView` fills the dial face with `fillPaint = @color/bg` (`#0A0D09`,
  `TimerRingView.java:47,140`), which is darker than the card's bottom, producing a visible
  "hole" rather than a dial.
- `TimerRingView` draws 60 ticks (`TimerRingView.java:124-138`), three glow arcs
  (`TimerRingView.java:156-162`) and a pulse dot every frame while running
  (`TimerRingView.java:179-181`). The two outer glow arcs have alpha 10-18, i.e. almost invisible
  but still cost a full-arc stroke each frame.
- No elevation/shadows are used; borders do all the work, which is correct for dark UI, but every
  card carries a 1dp stroke (`bg_card.xml:5`, `bg_card_hero.xml:8`) at 1.31-1.82:1 boundary
  contrast, below the 3:1 non-text threshold.

### 2.9 Scroll behavior

- Both roots are `NestedScrollView` with `android:fillViewport="true"` and `android:scrollbars="none"`
  (`activity_main.xml:2-8`, `activity_setup.xml:2-8`). Hiding scrollbars removes the scroll
  affordance on the long Setup screen.
- No `android:clipToPadding="false"` on either root, so content cannot scroll under the padding
  to create a fading edge; `android:fadingEdgeLength` is not set.
- `HorizontalScrollView` for chips (`activity_main.xml:128-133`) has `android:clipToPadding="false"`
  and `android:scrollbars="none"`, which is good, but the inner `LinearLayout` has
  `android:paddingEnd="4dp"` only, so the last chip can sit flush against the screen edge.
- Setup is ~1600dp tall with six equal cards and no sticky header; acceptable, but the
  14dp card gaps (`styles.xml:45`) make it feel like a list of identical boxes.

### 2.10 Accessibility semantics

- `MainActivity.java:137-138` sets `ACCESSIBILITY_LIVE_REGION_POLITE` on `tvRingStatus` and
  `tvStatePill`; both are re-set every second by the ticker, which can spam TalkBack even when
  the text is unchanged.
- Header logo on main (`activity_main.xml:25-29`) has `contentDescription="@string/app_name"`,
  so TalkBack announces the app name twice (logo + title). It should be `importantForAccessibility="no"`.
- Preset chips (`activity_main.xml:141-146`) are labeled "5", "10", … with no units; add
  `contentDescription="5 minutes"` (accessibility-only copy, no visible change).
- `TimerRingView` has no `contentDescription`; relies on `tvTime` being announced, which is fine,
  but the ring itself should be `importantForAccessibility="no"` to avoid a redundant focus stop.
- `btnClearLog` (`activity_main.xml:319-331`) clears the log with no confirmation; not a layout
  issue, but the 32dp target and 9sp label make it easy to hit accidentally.

## 3. Patch plan (XML-only unless noted)

Apply in order. No view IDs or Java behavior change.

### P0 — do now

**P0.1 Contrast: promote helper text to `muted` at 11sp+**
Files: `activity_main.xml:301,362`; `activity_setup.xml:333,460,596`.
```
android:textColor="@color/muted_dim"  ->  android:textColor="@color/muted"
android:textSize="9sp"               ->  android:textSize="11sp"
android:textSize="10sp"              ->  android:textSize="11sp"   (helper lines only)
```

**P0.2 Touch targets to 48dp**
`styles.xml`:
```
<!-- T9Chip -->
<item name="android:layout_height">48dp</item>          <!-- was 38dp -->
<item name="android:paddingTop">12dp</item>
<item name="android:paddingBottom">12dp</item>

<!-- T9SmallGhost -->
<item name="android:layout_height">48dp</item>          <!-- was 34dp -->
<item name="android:paddingTop">12dp</item>
<item name="android:paddingBottom">12dp</item>

<!-- T9StatusPill -->
<item name="android:minHeight">24dp</item>              <!-- was wrap only -->
<item name="android:paddingTop">5dp</item>
<item name="android:paddingBottom">5dp</item>
<item name="android:textSize">10sp</item>               <!-- was 9sp -->
```
`activity_main.xml:222,241` (chipAccess/chipDirect): add
`android:minHeight="48dp" android:paddingTop="12dp" android:paddingBottom="12dp"`.
`activity_main.xml:274-275` (btnRefreshIp): `48dp`/`48dp`.
`activity_setup.xml:27-28` (btnBack): `48dp`/`48dp`.
`activity_main.xml:321` (btnClearLog): `48dp` height, text 11sp.
`activity_main.xml:351` (tvFooter): add `android:minHeight="48dp"` and `android:paddingTop="12dp"`
`android:paddingBottom="12dp"`.

**P0.3 Primary action above the fold**
`activity_main.xml:85` FrameLayout `250dp` -> `210dp`.
`activity_main.xml:14-17` root padding `18dp` top -> `16dp`.
`activity_main.xml:131` chips row `6dp` -> `4dp`.
`activity_main.xml:154` tvNext `10dp` -> `8dp`.
`activity_main.xml:163` btnPrimary `12dp` -> `10dp`.
`activity_main.xml:169` reset row `10dp` -> `8dp`.
`activity_main.xml:213` (status chips row) `12dp` -> `8dp`.

**P0.4 Overdraw: one background draw**
`styles.xml:19`: `android:windowBackground` `@drawable/bg_app` -> `@color/bg`.
`bg_app.xml:15`: radial `startColor` `#1FC8FF2E` -> `#0DC8FF2E`, `gradientRadius` `520dp` -> `400dp`.

**P0.5 Chip state feedback**
Replace `bg_chip.xml` with a ripple over the existing selector:
```
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="#26C8FF2E">
    <item>
        <selector>
            <item android:state_selected="true">
                <shape android:shape="rectangle">
                    <solid android:color="@color/lime" />
                    <corners android:radius="100dp" />
                </shape>
            </item>
            <item>
                <shape android:shape="rectangle">
                    <solid android:color="@color/surface_2" />
                    <corners android:radius="100dp" />
                    <stroke android:width="1dp" android:color="@color/stroke" />
                </shape>
            </item>
        </selector>
    </item>
</ripple>
```
Remove the per-chip stroke by deleting the `<stroke>` line in the unselected item if you want the
cleaner segmented look; keep it if you want chip boundaries. Recommended: delete it.

**P0.6 Primary button pressed state**
`bg_btn_primary.xml:2` ripple `#33C8FF2E` -> `#33000000`.
`bg_btn_ghost.xml:2` ripple `#26C8FF2E` -> `#33C8FF2E`.

**P0.7 Radio button tint**
New file `app/src/main/res/color/radio_tint.xml`:
```
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true" android:color="@color/lime" />
    <item android:state_enabled="false" android:color="@color/muted_dim" />
    <item android:color="@color/stroke_bright" />
</selector>
```
`activity_setup.xml:243,252,261,270`: `android:buttonTint="@color/lime"` ->
`android:buttonTint="@color/radio_tint"`.

**P0.8 Switch to MaterialSwitch**
Replace the tag in four places (IDs unchanged; `MaterialSwitch extends SwitchCompat`, so
`MainActivity`/`SetupActivity` still compile):
- `activity_main.xml:204`
- `activity_setup.xml:366,403,439`
```
androidx.appcompat.widget.SwitchCompat  ->  com.google.android.material.materialswitch.MaterialSwitch
```
Add to `Theme.T9` (`styles.xml:4-29`):
```
<item name="colorSurfaceContainerLowest">@color/bg</item>
<item name="colorSurfaceContainerLow">@color/bg_elev</item>
<item name="colorSurfaceContainer">@color/surface</item>
<item name="colorSurfaceContainerHigh">@color/surface_2</item>
<item name="colorSurfaceContainerHighest">@color/surface_3</item>
<item name="colorOutlineVariant">@color/stroke</item>
```
and add `<color name="surface_3">#222B1B</color>` to `colors.xml`.

**P0.9 Typography normalization**
`styles.xml:60-64` (T9Label): `10sp` -> `11sp`, `0.14` -> `0.06`.
`styles.xml:50` (subtitle in main header, inline) `9sp` -> `11sp`, `0.16` -> `0.08`.
`styles.xml:121` (T9StatusPill) `9sp` -> `10sp`.
`activity_main.xml:231,250` status chips `9sp` -> `10sp`.
`activity_main.xml:124,157` (tvRingStatus, tvNext) `11sp` -> `12sp`.
`activity_main.xml:293` (tvIp) stays 28sp but add auto-size (P1).
`activity_setup.xml` subtitles `10sp` -> `11sp`; helper lines `10sp` -> `11sp`;
`tvAdbCommand` `10sp` -> `11sp`; `tvVersion` `9sp` -> `10sp`.

**P0.10 Spacing normalization**
`styles.xml:44-45`: T9Card padding `18dp` -> `20dp`, marginTop `14dp` -> `16dp`.
`styles.xml:53-54`: T9CardHero padding `18dp` -> `20dp`, marginTop `18dp` -> `20dp`.
`styles.xml:157-158`: T9Divider marginTop/Bottom `2dp` -> `0dp`.
`activity_main.xml:346`: footer marginTop `22dp` -> `24dp`.
`activity_main.xml:360`: version marginTop `20dp` -> `24dp`.
`activity_setup.xml:336`: divider marginTop `12dp` -> `8dp`.

### P1 — next

**P1.1 Auto-size the two dominant numbers**
`activity_main.xml:105-114` (tvTime): add
```
android:autoSizeTextType="uniform"
android:autoSizeMinTextSize="40sp"
android:autoSizeMaxTextSize="52sp"
android:autoSizeStepGranularity="1sp"
android:maxLines="1"
android:fontFeatureSettings="tnum"
```
keep `android:textSize="52sp"` as the fallback and `sans-serif-light`.

`activity_main.xml:284-293` (tvIp): add
```
android:autoSizeTextType="uniform"
android:autoSizeMinTextSize="18sp"
android:autoSizeMaxTextSize="28sp"
android:autoSizeStepGranularity="1sp"
android:maxLines="1"
android:breakStrategy="simple"
```

**P1.2 Text scaling on fixed-height controls**
- Preset chips: `android:layout_height="wrap_content"` with `android:minHeight="48dp"` and
  `android:maxLines="1"`.
- `activity_setup.xml:242,251,260,269` radios: `android:layout_height="wrap_content"`,
  `android:minHeight="48dp"`, `android:paddingTop="8dp"`, `android:paddingBottom="8dp"`,
  `android:maxLines="2"`.
- `activity_main.xml:222,241` status chips: `android:maxLines="2"` and remove `singleLine="true"`
  (use `maxLines`), keep `ellipsize="end"`.
- `activity_setup.xml:318-327` SeekBars: add `android:minHeight="48dp"`.

**P1.3 Focus states**
Add `state_focused` to `bg_chip.xml`, `bg_btn_ghost.xml` and `bg_pill.xml` via a selector item:
```
<item android:state_focused="true">
    <shape android:shape="rectangle">
        <solid android:color="@android:color/transparent" />
        <corners android:radius="100dp" />
        <stroke android:width="2dp" android:color="@color/lime" />
    </shape>
</item>
```
Place it before the default item in each selector.

**P1.4 Scroll affordance**
Both roots (`activity_main.xml:2-8`, `activity_setup.xml:2-8`):
```
android:clipToPadding="false"
android:fadingEdgeLength="24dp"
android:requiresFadingEdge="vertical"
```
Keep `android:scrollbars="none"` (modern default) but the fading edge now communicates scroll.

**P1.5 Ring dial face**
Option A (XML only): change `bg_card_hero.xml` so its bottom color equals `@color/bg` (`#0A0D09`),
removing the "hole". Option B (one Java line, visual only, no behavior change):
`TimerRingView.java:47` `inner = ContextCompat.getColor(context, R.color.bg)` ->
`R.color.surface`. Recommended: Option B, because it keeps the hero gradient.

### P2 — later / optional

- Edge-to-edge (Java, optional while targetSdk is 34):
  `WindowCompat.enableEdgeToEdge(getWindow())` in both activities' `onCreate`, plus
  `ViewCompat.setOnApplyWindowInsetsListener(root, …)` applying system-bar insets as padding to
  the root `LinearLayout`, with `clipToPadding="false"` on the `NestedScrollView`.
- Ring cost: reduce `tickCount` from 60 to 12 major ticks and remove the two outer glow arcs
  (`TimerRingView.java:124-138,156-162`); visual only.
- TalkBack spam: guard `tvRingStatus`/`tvStatePill` `setText` in `MainActivity.java:380-448` so it
  only fires when the value changed; add `importantForAccessibility="no"` to the header logo
  (`activity_main.xml:25-29`) and to `TimerRingView`.
- Log rows: `MainActivity.java:582-608` uses 9/11sp `setTextSize` and 4dp row padding; move to
  11/12sp and 8dp, add a 1dp divider or 8dp gap, and cap at 8 rows on phone widths.
- Large screens: add `layout-w600dp` variant or `android:maxWidth="600dp"` wrapper for the hero
  on tablets; current cards stretch full width.
- Accessibility copy: `contentDescription="5 minutes"` etc. on preset chips; `contentDescription`
  on status chips describing the action ("Open accessibility settings").
- Destructive separation on Setup: give the "DANGER ZONE" card a 1dp `@color/danger` stroke and
  24dp top margin, and make "RUN A RESET NOW" neutral while "RESET ALL SETTINGS & LOGS" keeps
  `@color/danger`.

## 4. Before/after tables

### 4.1 Spacing

| Token | Before | After |
|---|---|---|
| Screen gutter | 20dp | 20dp (keep) |
| Card padding | 18dp | 20dp |
| Card gap | 14dp | 16dp |
| Hero top margin | 18dp | 20dp |
| Hero internal gaps | 4/6/10/12dp | 4/8/8/10dp |
| Divider margins | 2dp/2dp | 0dp/0dp |
| Footer top margin | 22dp | 24dp |
| Root top padding (main) | 18dp | 16dp |
| Ring height | 250dp | 210dp |

### 4.2 Type scale

| Role | Before | After |
|---|---|---|
| Hero display (timer) | 54sp, sans-serif-light, fixed | 40-52sp auto, sans-serif-light, tnum |
| Screen title | 20sp / 19sp medium | 20sp medium (unified) |
| Section label | 10sp, 0.14em tracking, caps | 11sp, 0.06em tracking, caps |
| Card title / question | 13-14sp | 14sp |
| Body | 10-11sp | 12sp |
| Caption / meta | 9-10sp, `muted_dim` | 11sp, `muted` |
| Numeric data (IP) | 28sp mono, fixed | 18-28sp mono, auto |
| Button | 14 / 12 / 10sp | 14 / 12 / 11sp |
| Status pill | 9sp | 10sp |

## 5. What NOT to do (AI-slop avoidance)

- Do not add glassmorphism, blur, or a second neon glow; the single top radial glow is the
  identity. Reducing it to 8% is fine, doubling it is not.
- Do not add gradient text, outlined text, or drop shadows on a dark surface.
- Do not introduce new fonts, Compose, or any UI library beyond the Material dependency already
  in `build.gradle`.
- Do not nest cards inside cards or put a 1dp stroke on every surface; use tonal surface roles
  (surface / surface_2 / surface_3) for hierarchy.
- Do not ship 9sp (or smaller) text, and do not track uppercase labels beyond ~0.06em.
- Do not use fixed heights for text-bearing controls; verify at font scale 1.3 and 2.0.
- Do not use more than one accent for status; lime is brand/active, `ok`/`warn`/`danger` are
  reserved for outcomes, and neutrals for inactive.
- Do not add decorative animation (Lottie, shimmer, pulsing backgrounds); the ring's own
  progress animation is the only motion that earns its place.
- Do not center long body/helper text; left-align anything over one line.
- Do not add redundant micro-labels; if the ring already says "NEXT RESET", the card label
  should be quieter (smaller, lower contrast), not louder.
- Do not ship touch targets below 48dp or closer than 8dp apart.

## 6. Top 10 changes to apply now

1. Raise helper/secondary text from 9-10sp `muted_dim` to 11-12sp `muted` (`activity_main.xml:301,362`; `activity_setup.xml:333,460,596`).
2. Enforce 48dp touch targets on chips, status chips, icon buttons, clear/setup/back buttons and radios (`styles.xml` T9Chip/T9SmallGhost/T9StatusPill; XML minWidth/minHeight).
3. Reduce the hero ring from 250dp to 210dp and auto-size the timer text 40-52sp with `tnum` so the start button is above the fold on 640dp screens.
4. Replace `SwitchCompat` with `MaterialSwitch` and add green surface-container roles to `Theme.T9` so switches render as Material 3 and match the palette.
5. Add `color/radio_tint.xml` and apply it to all four strategy radios so unchecked circles read as inactive.
6. Give preset chips a ripple and drop the per-chip 1dp stroke for a cleaner segmented control.
7. Change the primary button ripple to a dark scrim and reduce T9Label tracking from 0.14 to 0.06.
8. Normalize card padding 18->20dp, card gaps 14->16dp, divider margins 2->0dp, and reduce the top radial glow to 8%.
9. Add vertical fading edges plus `clipToPadding="false"` to both `NestedScrollView` roots and set `windowBackground` to `@color/bg`.
10. Auto-size the IP value (18-28sp) and allow the two status chips to wrap to two lines so IPv6 and scaled text do not clip.
