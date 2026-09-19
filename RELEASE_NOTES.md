# 🚀 Velocity Browser v3.0 Release Notes

**Velocity Browser** is a 100% native Android Markdown browser built without WebView/WebKit, delivering lightning-fast, distraction-free web reading.

---

## 🌟 What's New in v3.0 (Build 3)

### 🖼️ Image Viewing & Native SVG Support
* **Native SVG Vector Rendering**: Added full vector graphic support via `AndroidSVG` to natively decode and render `.svg` images, logos, math formulas, and icons (e.g., on Wikipedia and GitHub).
* **Resolved Bitmap Decoding Errors**: Fixed an issue where Wikipedia media description pages (`/wiki/File:...`) were mistakenly treated as raw images, now properly loading them as readable web pages.
* **Corrupted Cache Prevention**: Added HTML payload detection to ensure error pages and HTML responses never corrupt the image disk cache; invalid entries are automatically cleaned.
* **Direct Gallery Saving**: Download and save images directly to device storage / Downloads with instant MediaStore gallery indexing.

### 🌐 Smart Link Routing & External System Delegation
* **Focused Native Reader**: Velocity exclusively renders web documents (`.html`, `.htm`, `.xhtml`, `.php`, `.asp`, etc.), Markdown documents (`.md`, `.markdown`), and plain text / code files (`.txt`, `.json`, `.xml`, `.csv`, source code).
* **System Delegation**: Non-web binary and media formats (`.pdf`, `.epub`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`, `.zip`, `.rar`, `.7z`, `.apk`, `.mp3`, `.wav`, `.mp4`, `.mkv`, `.torrent`, etc.) and custom schemes (`mailto:`, `tel:`, `sms:`, `intent:`, `magnet:`) are automatically delegated to system applications via `Intent.ACTION_VIEW` with appropriate MIME types.
* **Live Content-Type Detection**: Inspects server response headers during HTTP fetch; if a non-renderable stream is encountered, Velocity safely forwards it to external applications without rendering binary garbage.

### 📑 Table of Contents & Anchor Navigation
* **Enhanced Heading ID Extraction**: Table of contents parser now detects MediaWiki inner spans (`<span class="mw-headline" id="...">`), name attributes, and preceding anchor tags.
* **5-Tier Anchor Resolution**: Table of Contents drawer and in-page anchor links resolve using a multi-stage cascade (URL decoding, slug normalization, heading list matching, `{#id}` tag indexing, and rendered text search) with a `-16dp` offset for smooth reading.

### 📊 Advanced Table Processing & Cell Link Interaction
* **Nested Table Unnesting**: Automatically extracts complex multi-level tables into clean standalone GFM Markdown tables with bidirectional navigation jump links (`[📊 Sub-table: ...]` and `[⬆️ Back to Main Table]`).
* **Single-Value Table Inlining**: Detects single-cell or key-value subtables and renders them cleanly inline, eliminating unnecessary subtable generation.
* **Interactive Table Links**: Configured `TableAwareMovementMethod` so that links inside Markdown table cells are fully responsive to touch.

### 🦆 DuckDuckGo Search Engine & Attribution
* **Provider Attribution**: Added clear attribution banners (`> 🦆 These search results are provided by DuckDuckGo`) in search loading states and result pages.
* **Cascading Fallback Engine**: Multi-endpoint search engine with automatic cascading fallback to DuckDuckGo Lite ensures uninterrupted searches without HTTP 202 throttling.

---

## 🛠️ Technical Details
* **Version Name**: `3.0`
* **Version Code**: `3`
* **Compile / Target SDK**: `Android 36` (Android 16 preview / Android 15 compatible)
* **Minimum SDK**: `21` (Android 5.0 Lollipop)
* **Architecture**: 100% Native Android UI (TextView + Markwon Markdown rendering, QuickJS engine, Zero WebView)
