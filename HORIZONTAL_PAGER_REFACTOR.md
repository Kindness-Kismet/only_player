# Root Tab HorizontalPager Refactor

## Goal
Match SukiSU Miuix root-tab behavior:
- finger-drag horizontal swipe shows adjacent tab content live
- clicking a distant tab scrolls across intermediate pages

## Why smali could not do this
Only-Player previously used a single `NavHost` with 4 root destinations.
SukiSU uses Compose `HorizontalPager` + `PagerState`.
Without source, smali cannot compose neighboring pages under the finger.

## Architecture change
Root tabs are no longer switched by `NavHost` destination replace.

```
RootTabPager
├── HorizontalPager (4 pages, beyondViewportPageCount=1)
│   ├── page0 HomeTabNavHost    (own NavHostController)
│   ├── page1 CloudTabNavHost   (own NavHostController)
│   ├── page2 FavoritesTabNavHost
│   └── page3 SettingsTabNavHost
└── RootScaffold bottom bar (selectedIndex follows pager)
```

- Root swipe/tab click => `pagerState.animateScrollToPage` / user drag
- Nested screens (folder, search, settings subpages, cloud browse) stay in each tab's NavHost
- When nested, `userScrollEnabled=false` so vertical lists win and bottom bar hides

## Files changed
- `app/.../navigation/RootTabPager.kt` (new)
- `app/.../navigation/RootScaffold.kt` (accept selectedIndexProvider; no longer owns NavController)
- `app/.../MainActivity.kt` (use RootTabPager)
- `app/.../navigation/MediaNavGraph.kt` (optional tab-switch callbacks)
- `app/.../navigation/FavoritesNavGraph.kt` (optional open-folder callback)
- `core/model/.../ApplicationPreferences.kt` (default floating bar = true)

## Build notes
Project prefers JDK 25 + Android SDK. Local validation may require:
- JDK 25+
- `local.properties` with `sdk.dir=...`
- restore `android-jvm = "25"` and JDK check if temporarily lowered

```bash
python scripts/build.py build-apk --abi arm64-v8a --build-type debug
```
