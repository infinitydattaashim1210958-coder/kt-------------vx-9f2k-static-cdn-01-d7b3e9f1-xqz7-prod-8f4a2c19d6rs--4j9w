# SWADHYAY UX Specification

Grounded in `ShellActivity` and related presentation code. UX (behavior/IA) is specified separately from UI (visual system); both share one Views implementation contract.

## A. Information architecture

### Top-level tabs

| Tab | Purpose | Primary task |
|-----|---------|--------------|
| **হোম** | Catalogue + identity + continue | Open a corpus or library entry |
| **লাইব্রেরি** | Digital library catalog | Browse/download/open books |
| **বুকমার্ক** | Saved positions/snippets | Resume a saved item |
| **খুঁজুন** | Cross-corpus search | Find text → open result |
| **সেটিংস** | Preferences and app meta | Adjust reader/appearance; legal/support |

### Secondary destinations (Activities)

Veda reader, Ramayana, Mahabharata, Gita, Library DB/HTML readers, Agent (শাস্ত্র-সহায়ক), Listening mode, Splash.

### Home catalogue rules

- All current `homeSections` remain visible.
- Implemented actions navigate as today.
- `HomeAction.Soon` must show honest “শীঘ্রই আসছে” feedback and reduced affordance (not deleted, not fake-enabled).

### Back behavior

- Settings sub-screen → root Settings (existing `settingsScreen` / back header).
- Readers → system Back finishes Activity → previous shell tab.
- Tab switches cancel in-flight `renderJob` / `libraryJob` / search jobs (preserve).

## B. Task flows (preserve semantics)

1. **Home → corpus** — Vedas/Ramayana/Mahabharata/Gita/Agent as current `onHomeSectionTap`.
2. **Home → Library tab** — `HomeAction.Library` → `show(LIBRARY)`.
3. **Continue** — if `UserPrefs.continueFlow` set, open matching reader with stored ids (no inventing unsupported kinds).
4. **Bookmark open/remove** — existing prefs APIs; never drop legacy unresolved rows in data layer.
5. **Search** — debounce/cancel as implemented; empty query and no-hits states.
6. **Settings** — nested screens; accent via `AppColors.applyAccent` + recreate pattern as today.
7. **Update card** — existing `UpdateChecker` / download path.

## C. Interaction states

| State | UX requirement |
|-------|----------------|
| Loading | Visible progress or skeleton; cancel on tab leave |
| Empty | Bengali message + optional next step (e.g. “রিডারে বুকমার্ক চাপুন”) |
| Error | Message + retry when operation is retryable |
| Soon | Distinct visual + toast; not primary CTA styling |
| Selected tab | Color **and** indicator/weight/background |
| Destructive | Confirm only if already required by current code |

No infinite spinner, silent failure, or control that looks enabled but does nothing without feedback.

## D. Accessibility & localization

- Interactive targets ≥ **48dp** height where practical.
- `contentDescription` on icon-only and tab items (Bengali labels).
- Selected tab not color-only.
- Respect font scale (`sp`); avoid fixed widths that clip Bangla/Devanagari.
- Keep Bengali UI copy; do not rewrite scriptural strings.

## E. UI design system contract

Package: `com.kyronix.swadhyaa.ui.components`

Primitives (Views factory helpers, not a new framework):

- **UiTokens** — spacing, radius, type sizes referencing `AppColors`
- **UiText** — screen title, section header, body, meta, muted
- **UiCard** — surface container with optional border (restrained; Glow optional)
- **UiEmpty** — empty/error/soon blocks
- **UiTabItem** — bottom nav cell with selected indicator
- **UiPrefRow** — settings row (icon/label/optional value)
- **UiPrimaryButton** / secondary text action

Rules:

- No Compose; Material where already available.
- Accent only through `AppColors` after `applyAccent`.
- No one-off hex in new code.
- Shell extracts builders incrementally; avoid one-shot full rewrite of 1537 lines.

## F. Theme policy

- Visual **dark-only** continues to match legacy and `AppColors` docs.
- Stored `theme` preference remains for migration parity; UI redesign does not pretend light mode works.
- Accents: gold (default), emerald,
... 
