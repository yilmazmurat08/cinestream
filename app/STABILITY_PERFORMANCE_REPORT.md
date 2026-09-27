# CineStream IPTV - Stability, Memory & Performance Report

## Executive Summary
This report details the architectural enhancements, memory optimizations, and resilience improvements implemented across CineStream IPTV for production-grade stability on low-RAM devices and large playlists (10,000 to 100,000+ items).

---

## 1. M3U Streaming Parser & Bounded Memory Allocation (Zero Full-RAM Loading)
* **Problem**: Previously, M3U downloads buffered entire multi-megabyte payloads in memory via `response.body?.string()`, leading to rapid OutOfMemory (OOM) errors during garbage collector pressure on low-memory devices.
* **Solution**: Implemented `BoundedInputStream` with a strict 200 MB hardware safety envelope and line-by-line `BufferedReader` processing in `IPTVRepository.kt`. The payload is streamed directly into Room database batch insertions (250 items/batch) with deterministic ID generation and immediate object garbage collection.

---

## 2. Atomic Staging Tables & Fail-Safe Rollback Architecture
* **Problem**: Network disconnects or corrupt streams during synchronization previously wiped or half-deleted existing playlists, leaving the user with an empty or broken channel list.
* **Solution**: Introduced `iptv_items_staging` table (Room Migration 3->4).
  1. All new stream entries are written exclusively into the staging table.
  2. If the parse encounters a network failure or malformed termination, the staging entries are rolled back and cleared, preserving 100% of the active database intact.
  3. Upon parsing completion, an atomic `@Transaction` promotes staging items to active `iptv_items`, preserving user favorite statuses across updates.

---

## 3. Player Disk I/O Throttling & Progress Debouncing
* **Problem**: `PlayerScreen.kt` and `VideoPlayerScreen.kt` were executing continuous disk writes every second to update watch progress and watch-time telemetry.
* **Solution**: Watch-time tracking is debounced to a 10-second interval, and progress updates are written only on significant changes (>10s) or lifecycle pauses/stops.

---

## 4. ViewModel Memory Offloading & Query-Level Flow Filtering
* **Problem**: `IPTVViewModel.kt` previously executed multiple in-memory `filter` and `groupBy` passes over the entire 50k+ channel dataset (`_allItems`), creating multiple copies of large object graphs.
* **Solution**: Offloaded filtering to indexed SQLite queries in `IPTVDao`:
  - `getItemsByTypeFlow(type)` streams only the active tab type (e.g. Live TV, Movies, or Series).
  - `getDistinctCategoriesFlow()` fetches only lightweight category strings (a few hundred bytes).
  - Featured Movie updates listen exclusively to movie item emissions.

---

## 5. Bounded Storage & Cache Maintenance
* **Bounded Search History**: Maximum 100 entries via atomic Room limit queries.
* **Bounded Continue Watching**: Maximum 50 items with automated pruning.
* **Bounded TMDB Person Details Cache**: Maximum 200 items in SQLite cache.
* **Coil Cache Management**: Max 150 MB disk cache limit with proactive pruning during app startup in `StorageOptimizer.kt`, and full memory trimming on `onTrimMemory` / `onLowMemory`.

---

## 6. Automated Test & Stress Suite Verification
The complete unit, stress, and regression suite was executed and passed with `BUILD SUCCESSFUL`:

1. **`testBoundedInputStream_ExceedsLimit_ThrowsIOException`**: Validates early abort and memory protection when payloads exceed safety limits.
2. **`testStreamingM3UParserWith10kItems_StressAndIntegrity`**: Confirms streaming parsing of 10,000 channels without heap bloat, staging promotion, and active database integrity.
3. **`testStagingTableRollback_WhenStreamFailsMidway_OldDataPreserved`**: Simulates mid-stream connection drops and verifies old user data remains 100% preserved.
4. **`testEmptyPlaylistAndMalformedM3UHandling`**: Verifies graceful handling of corrupt, malformed M3U headers and invalid stream URLs.
5. **`testFavoritePreservationDuringStagingUpdate`**: Confirms user favorites are retained when remote playlists are updated or re-synced.
6. **`testBoundedSearchHistoryLimit`**: Validates automatic pruning of search history to the 100-item ceiling.
7. **`testBoundedContinueWatchingLimit`**: Confirms continue-watching records are bounded to 50 items.
8. **`testDistinctCategoriesQueryEfficiency`**: Verifies distinct category extraction without full-table memory copies.
