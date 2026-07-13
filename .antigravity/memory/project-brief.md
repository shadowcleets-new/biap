# Project Brief: biap (Business Intelligence App)

## 1. Executive Summary
biap is a native Android Business Intelligence application designed to provide rich data visualizations, reports, and analytical insights. All features are baked on-demand as directed by the user.

## 2. Architectural Overview
- **Platform:** Native Android (Kotlin)
- **UI Framework:** Jetpack Compose
- **Build System:** Gradle (Kotlin DSL, AGP 9.2.1, Gradle Wrapper 9.4.1)
- **Minimum SDK:** Android 24+
- **Target SDK:** Android 36

## 3. Database Schema & Data Models
- **SizeInventory:** `size: String`, `stock: Int`, `price: Long` (cents)
- **Design:** `id: String`, `name: String`, `category: String`, `imagePath: String?`, `sizeInventories: List<SizeInventory>`, `customFields: Map<String, String>`
- **BusinessIntelligenceData:** `designs: List<Design>`, `globalCustomFields: Set<String>`

## 4. Active Endpoints & Integrations
- Local JSON storage: saved at `context.filesDir/bi_data.json`

## 5. Development Backlog
- [x] Create native Android project structure using Compose template.
- [x] Configure persistent memory bank and project files in dedicated directory.
- [x] Rename project to 'biap' (refactor packaging, directories, and configuration).
- [x] Implement design upload, size-specific pricing and stock tracking.
- [x] Implement structured 3-piece custom and global attributes (Top, Bottom, Dupatta, General).
- [x] Configure PickVisualMedia API for safe, zero-permission gallery photo selection.
- [x] Establish PerformanceManager and rememberPerformanceProfile system for battery/hardware-aware animation throttling.
- [x] Introduce defensive input text boundaries, length restrictions, and character digit sanitization.
- [x] Add Karigars (artisans/tailors) assignment field on creation and dynamic re-assignment on details view.
- [x] Implement complete inline edit mode in DesignDetailScreen supporting size configurations, image editing, name, category, custom attributes, and Karigars.
- [x] Integrate Google Sign-in authentication and background database encryption and synchronization with Google Drive's appDataFolder.
- [x] Create high-definition brand logo and splash screen background, and implement an animated Contemporary Heritage splash screen mocked in Google Stitch.
- [x] Deactivate dynamic colors in Theme.kt to protect brand integrity.
- [x] Implement curated Heritage Warm Palette (Canvas Cream, Muted Bronze, Warm Charcoal, Ivory, Obsidian) in Light/Dark color schemes.
- [x] Refactor shape system (unify CornerLarge to 16.dp, CornerMedium to 12.dp, CornerSmall to 8.dp).
- [x] Map typography to Serifs (Noto Serif/Playfair Display for headlines) and Sans-Serifs for details.
- [x] Implement Compose BackHandler inside Add/Edit screens to trigger warning alert dialog on unsaved changes.
- [x] Add validation warning prompt on adding custom sizes if a duplicate size already exists.
- [x] Add snackbar visual indicators for sync completion or sync failure (with a retry option).
- [x] Adjust target contrast ratios on outlines/placeholders and wrap chips in Modifier.minimumInteractiveComponentSize() for touch targets.
- [x] Clean up Detail view-mode specs to hide empty/unspecified custom fields.
- [x] Implement robust database backup (.bak) / corrupt recovery (.corrupt) cycle and relative image file resolution (portable paths) for absolute data persistence across app updates.
- [x] Migrate monolithic file-based JSON storage (bi_data.json) to relational SQLite caching using Room Database.
- [x] Integrate entity-level updatedAt metadata tracking and deletedDesigns tombstones to enable LWW sync conflict resolution.
- [x] Perfect custom sizing & pricing flow (logical sorting, data-loss recovery cache, duplicate validation, float-to-cents parsing buffer, and unit tests).

