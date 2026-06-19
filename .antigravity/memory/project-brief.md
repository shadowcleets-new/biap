# Project Brief: biap (Business Intelligence App)

## 1. Executive Summary
biap is a native Android Business Intelligence application designed to provide rich data visualizations, reports, and analytical insights. All features are baked on-demand as directed by the user.

## 2. Architectural Overview
- **Platform:** Native Android (Kotlin)
- **UI Framework:** Jetpack Compose
- **Build System:** Gradle (Kotlin DSL, AGP 9)
- **Minimum SDK:** Android 24+
- **Target SDK:** Android 36

## 3. Database Schema & Data Models
- **SizeInventory:** `size: String`, `stock: Int`, `price: Double`
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
