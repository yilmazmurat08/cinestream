# CinemaStream Adaptive & Responsive Layout Architecture Report

## 1. Overview
This report details the comprehensive audit and full refactoring of **CinemaStream IPTV**'s adaptive layout system. The goal was to replace scattered hardcoded sizing with a centralized, fluid, multi-form-factor design supporting compact phones, foldables, tablets, landscape/portrait orientations, and multi-window split-screen modes.

---

## 2. Central Source of Truth: `WindowSizeUtils.kt`
The centralized `AppAdaptiveLayout` class dynamically computes layout parameters using Jetpack Compose's `WindowWidthSizeClass`, `WindowHeightSizeClass`, and `LocalConfiguration.current`:

- **Form Factor Detection**:
  - `isCompact`: Handheld phones in portrait mode ($< 600\text{ dp}$).
  - `isMedium`: Foldables, large phones in landscape, or small tablets ($600\text{ dp} - 839\text{ dp}$).
  - `isExpanded`: Full-sized tablets, DeX desktop interfaces ($\ge 840\text{ dp}$).
  - `isLandscape`: Orientation detection accounting for height constraints.
  - `isHeightCompact`: Height $< 480\text{ dp}$ (landscape phones), which compresses vertical bars and paddings to prevent clipping.

- **Dynamic Metrics Table**:
| Parameter | Compact (Portrait Phone) | Medium (Foldable / Mini-Tablet) | Expanded (Tablet / DeX) | Compact Height (Landscape Phone) |
|---|---|---|---|---|
| `gridColumns` | 2 | 4 | 6 | 4 |
| `cardWidth` | 135 dp | 170 dp | 210 dp | 140 dp |
| `cardHeight` | 195 dp | 245 dp | 295 dp | 190 dp |
| `heroBannerHeight` | 280 dp | 340 dp | 420 dp | 210 dp |
| `navBarHeight` | 74 dp | 80 dp | 84 dp | 56 dp |
| `playerPlayButtonSize` | 64 dp | 74 dp | 88 dp | 52 dp |
| `playerSecondaryControlSize` | 44 dp | 50 dp | 56 dp | 38 dp |
| `screenPadding` | 16 dp | 24 dp | 32 dp | 12 dp |
| `dialogMaxWidth` | 460 dp | 540 dp | 640 dp | 480 dp |

---

## 3. Screen & Component Refactoring Summary

### 1. `HomeScreen.kt`
- **Header & Navigation Bar**: Integrated `rememberAppAdaptiveLayout()` into `CinemaStreamTopBar` and `CinemaStreamBottomNav`.
- **Hero Carousel**: Synchronized with `layout.heroBannerHeight` and aspect-ratio bounds to prevent layout overflow in split screen and landscape.
- **Top 10 Section & Media Rows**: Standardized content paddings and card sizes to use `layout.screenPadding` and `layout.cardWidth`.

### 2. `VideoPlayerScreen.kt` & `PlayerScreen.kt`
- **Overlap Fix**: Separated timeline progress bar and primary playback controls (`Play/Pause`, `Rewind`, `Forward`) into distinct vertical flex rows to prevent any visual overlap.
- **Control Scaling**: Playback buttons (`layout.playerPlayButtonSize`), secondary buttons (`layout.playerSecondaryControlSize`), and typography now scale dynamically.
- **Landscape Phone Optimization**: When `isHeightCompact` is true, heights and icon sizes contract gracefully.

### 3. `FolderGridScreen.kt` & Grid Layouts
- **Dynamic Columns**: Replaced hardcoded column counts with `adaptiveLayout.gridColumns` (2 columns on portrait phones, 4 on medium/foldable screens, 6 on large tablets).
- **Grid Spacing & Edge Padding**: Bound to `adaptiveLayout.gridSpacing` and `adaptiveLayout.screenPadding`.

### 4. `LiveTvScreen.kt`
- **Dual-Pane & Three-Pane Support**: Fluid landscape view utilizing category drawer (25%), channel list (40-75%), and transparent purple EPG guide (35%).
- **Portrait Responsive Accordion**: Integrated `adaptiveLayout.screenPadding` and flexible chips row.

### 5. Modal Sheets & Dialogs
- **Refactored Components**: `DetailScreen.kt`, `SeriesDetailScreen.kt`, `PlaylistAddDialog.kt`, `ProPaywallDialog.kt`, `SearchPopupDialog.kt`, `PersonDetailDialog.kt`, `MediaCardPreviewDialog.kt`.
- **Constraint Unification**: Dialogs and bottom cards now clamp their width using `.widthIn(max = layout.dialogMaxWidth)` with adaptive height bounds (`heightIn(max = if (layout.isLandscape) 420.dp else ...)`), preventing dialogs from stretching awkwardly on widescreen tablets or clipping on landscape phones.

---

## 4. Verification & Quality Assurance
- **Full Clean Compilation**: Verified with `compile_applet` (100% build success).
- **Text Safety**: All dynamic text elements enforce `maxLines` and `TextOverflow.Ellipsis`.
- **Touch Targets**: Minimum interactive component sizes maintain accessibility standards ($\ge 48\text{ dp}$).
