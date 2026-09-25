# 🚀 Velocity Browser v4.0 Release Notes

**Velocity Browser** is a 100% native Android Markdown browser built without WebView/WebKit, delivering lightning-fast, distraction-free web reading.

---

## 🌟 What's New in v4.0 (Build 4)

### 🎨 Material You & Material 3 UI Redesign
* **Floating Capsule Navigation Bars**: Rebuilt the interface with sleek, floating pill action bars inspired by modern Material 3 and Obsidian aesthetics.
* **Scroll-Responsive Auto Hide/Show**: All top and bottom floating UI components glide away smoothly on scroll down to offer an immersive, full-screen reading experience, and reappear instantly on scroll up.
* **Material You Dynamic Color Palette**: Swapped old accent tones for refined Material You tonal palettes (`#A8C7FA` primary blue, `#121214` true dark background, `#242529` elevated cards).
* **Divided Dual Menu Architecture**:
  * **Top-Right 3-Dots Menu (Page Options)**: Dedicated reading controls — Table of Contents / Outline, Text-to-Speech playback, Dynamic Themes, Offline Markdown Saving, In-page Search, and Reload.
  * **Bottom-Right Menu (App Navigation)**: Global browser controls — Reading Hub Home, Open Local `.md` File, Saved Documents Library, Browsing History, Tab Overview, and Tab Dismissal.

### 📋 Obsidian-Style Modal Sheets
* **Unified Card Bottom Sheets**: Upgraded all modal sheets (Saved Documents, Browsing History, Article Outline / TOC, Theme Selector, and App Menus) to an Obsidian-inspired card layout featuring rounded containers, clean vector line icons, subtle divider lines, and tactile ripple feedback.
* **Empty State Illustrations & Direct Deletion**: Saved Documents and History sheets now include contextual empty states and one-tap item removal.

### 📝 Native Text Selection & Actions
* **Full Text Selection Support**: Enabled native long-press text selection throughout rendered Markdown articles with draggable selection handles.
* **System Contextual Toolbar**: Access standard Android text actions (Copy, Share, Select All, Web Search) directly from any paragraph or heading without breaking link touch targets.

### 💾 Enhanced Offline Markdown & Storage Library
* **Direct Saved Document Launching**: Fixed an issue where tapping saved documents from the library did not reveal the reader view.
* **Obsidian-Compatible File Storage**: Offline articles are saved in private internal storage (`/data/user/0/com.velocity.browser/files/offline_bookmarks/{id}.md`) with standard YAML frontmatter metadata (`title`, `url`, `saved_at`) for seamless compatibility with external Markdown editors.

### 📱 Adaptive Navigation & Edge-to-Edge Compatibility
* **Edge-to-Edge System Bar Insets**: Added window inset handlers with backwards-compatible fallbacks so floating navigation capsules float above system 3-button navigation bars and gesture pill bars across all Android versions (API 21 through Android 15/16 preview).

---

## 🌟 Previous Releases

### 📦 v3.0 (Build 3)
* Native SVG vector decoding via `AndroidSVG`.
* Smart link routing with system intent delegation for non-web binaries (`.pdf`, `.epub`, `.docx`, `.apk`, media).
* 5-tier Table of Contents and anchor scroll cascade.
* GFM sub-table extraction and inlining for complex HTML tables.
* DuckDuckGo fallback search engine with attribution banners.

---

## 🛠️ Technical Details
* **Version Name**: `4.0`
* **Version Code**: `4`
* **Compile / Target SDK**: `Android 36` (Android 16 preview / Android 15 compatible)
* **Minimum SDK**: `21` (Android 5.0 Lollipop)
* **Architecture**: 100% Native Android UI (Markwon Markdown rendering, QuickJS engine, Zero WebView)
