# OpenUI

[![JitPack](https://jitpack.io/v/rowanlabs/openui.svg)](https://jitpack.io/#rowanlabs/openui)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

OpenUI is an Android UI classes SDK by Rowan Vex (Rowan Labs), written in Java. It provides animated custom views and a configurable list of recommended apps.

## SDK overview

- Package: `com.rowan.android.uiclasses`
- Current version: `1.0.1.1` (published)
- Minimum Android version: API 24
- Language: Java 11

| Class | Type | Purpose |
|---|---|---|
| `NeuralOrbView` | Custom `View` | Animated, dotted orb with calm and thinking states. |
| `OceanWaveView` | Custom `View` | Rounded animated water card with an icon and configurable label. |
| `MoreAppsView` | Custom vertical `LinearLayout` | Loads and displays app recommendations from a JSON endpoint, with cached data and loading placeholders. |
| `RowanUi` | Utility class | SDK version information and optional debug logging. |

`MoreAppsView` also exposes `AppItem`, `OnAppClickListener` and `OnLoadListener` as nested public types.

## Installation

Add JitPack to `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the SDK to the app module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.rowanlabs:openui:1.0.1.1'
}
```

The SDK has a dependency on AndroidX AppCompat. `MoreAppsView` uses the `INTERNET` permission, which is declared by the SDK manifest and merged into the consuming app.

## NeuralOrbView

Add the view in XML. Its size comes from its layout dimensions; the default measured size is 220dp by 220dp when the parent allows it.

```xml
<com.rowan.android.uiclasses.NeuralOrbView
    android:id="@+id/neuralOrb"
    android:layout_width="250dp"
    android:layout_height="250dp" />
```

There are no custom XML attributes. Configure the orb from Java:

```java
NeuralOrbView orb = findViewById(R.id.neuralOrb);
orb.setOrbColor(0xFF2EE6A6); // ARGB color
orb.setThinking(true);       // faster, more turbulent animation
orb.setThinking(false);      // calm idle animation
orb.setAnimating(false);     // pause
orb.setAnimating(true);      // resume
```

Public methods: `setOrbColor(int)`, `setThinking(boolean)` and `setAnimating(boolean)`.

## OceanWaveView

This view draws a rounded container, animated wave, optional bordered icon circle, and label. Add the `app` namespace to the root layout and use the `owv_` prefix for its custom XML attributes.

```xml
<com.rowan.android.uiclasses.OceanWaveView
    android:id="@+id/oceanWave"
    android:layout_width="160dp"
    android:layout_height="240dp"
    app:owv_waveColor="#216838"
    app:owv_waveLevel="0.35"
    app:owv_cornerRadius="10dp"
    app:owv_containerColor="#282828"
    app:owv_outerBorderColor="#FFFFFF"
    app:owv_outerBorderWidth="2dp"
    app:owv_icon="@drawable/ic_my_icon"
    app:owv_iconTint="#FFFFFF"
    app:owv_iconSize="24dp"
    app:owv_iconCircleSize="56dp"
    app:owv_iconBorderColor="#FFFFFF"
    app:owv_iconBorderWidth="2dp"
    app:owv_text="My label"
    app:owv_textColor="#FFFFFF"
    app:owv_textSize="16sp"
    app:owv_textMaxLines="2"
    app:owv_textEllipsize="end"
    app:owv_textPosition="aboveWave" />
```

### OceanWaveView XML attributes

All custom attributes are listed below. Optional attributes omitted from XML use the defaults indicated by the view: wave level `0.35`, wave speed `1`, transparent container, no outer border, 60dp corner radius, 22dp icon, 52dp icon circle, 14sp label, one label line, centered alignment, 5dp top label padding and 12dp horizontal label padding.

| Attribute | Format / values | Description |
|---|---|---|
| `owv_waveColor` | color | Front wave color. |
| `owv_waveLevel` | float, 0–1 | Water fill level. |
| `owv_waveAmplitude` | dimension | Wave height/amplitude. |
| `owv_waveSpeed` | float | Wave animation speed multiplier; `1` is normal. |
| `owv_cornerRadius` | dimension | Card corner radius. |
| `owv_containerColor` | color | Color of the area above the water; transparent by default. |
| `owv_outerBorderColor` | color | Card border color. |
| `owv_outerBorderWidth` | dimension | Card border width; `0` disables it. |
| `owv_icon` | drawable reference | Icon drawable; when omitted, the built-in arrow is used. |
| `owv_iconTint` | color | Icon tint. |
| `owv_iconSize` | dimension | Icon size. |
| `owv_iconCircleSize` | dimension | Diameter of the icon circle. |
| `owv_iconBorderColor` | color | Icon circle border color. |
| `owv_iconBorderWidth` | dimension | Icon circle border width. |
| `owv_text` | string | Label text. |
| `owv_textColor` | color | Label color. |
| `owv_textSize` | dimension | Label size, typically in `sp`. |
| `owv_textFontFamily` | font reference or string | Font resource such as `@font/my_font` or family name such as `sans-serif-medium`. |
| `owv_textFontStyle` | `normal`, `bold`, `italic`, `boldItalic` | Label typeface style. |
| `owv_textMaxLines` | integer | Maximum label lines; default `1`; `0` or less means unlimited. |
| `owv_textEllipsize` | `none`, `start`, `middle`, `end` | Truncation behavior. `start` and `middle` are supported only for one line. |
| `owv_textAlign` | `left`, `center`, `right` | Label alignment. |
| `owv_textLineSpacingExtra` | dimension | Extra line spacing. |
| `owv_textLineSpacingMultiplier` | float | Line spacing multiplier. |
| `owv_textLetterSpacing` | float | Letter spacing in em, like `android:letterSpacing`. |
| `owv_textUppercase` | boolean | Convert the label to uppercase. |
| `owv_textPaddingTop` | dimension | Top padding when label position is `top`; default `5dp`. |
| `owv_textPaddingHorizontal` | dimension | Horizontal label padding; default `12dp`. |
| `owv_textPosition` | `top`, `aboveWave` | Put the label at the top or centered in the space above the wave. |

### OceanWaveView Java API

The view can also be configured at runtime. Dimension arguments with `Px` in their semantics are pixels.

```java
oceanWave.setWaveLevel(0.6f, true); // animate to the new level
oceanWave.setWaveColor(0xFF2196F3);
oceanWave.setText("Next");
oceanWave.setAnimating(false);
```

Public methods include `setWaveColor`, `setContainerColor`, `setWaveAmplitude`, `setWaveSpeed`, `setCornerRadius`, `setOuterBorderColor`, `setOuterBorderWidth`, `setIcon`, `setIconResource`, `setIconTint`, `setIconBorderColor`, `setIconBorderWidth`, `setText`, `setTextColor`, `setTextSize`, `setTextMaxLines`, `setTextEllipsize`, `setTextAlign`, `setTextLineSpacing`, `setTextLetterSpacing`, `setTextUppercase`, `setTextPaddingTop`, `setTextPaddingHorizontal`, `setTextPosition`, `setTextTypeface`, `setTextFontResource`, `setTextFontFamily`, `setTextFontStyle`, `setWaveLevel` and `setAnimating`.

## MoreAppsView

`MoreAppsView` automatically requests its configured endpoint when attached. It displays cached JSON immediately when available, fetches fresh data in the background and updates the list when the response changes. Without cached data it displays animated skeleton rows while loading. App icons use an internal memory and disk cache.

```xml
<com.rowan.android.uiclasses.MoreAppsView
    android:id="@+id/moreAppsView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:rowan_apiUrl="https://example.com/more_apps.json"
    app:rowan_listCount="4"
    app:rowan_hideWhenEmpty="true"
    app:rowan_excludeOwnApp="true"
    app:rowan_itemBackgroundColor="#FFFFFF"
    app:rowan_itemBorderColor="#E6E8EB"
    app:rowan_logoSize="48dp"
    app:rowan_logoCornerRadius="12dp"
    app:rowan_appTitleColor="#1B1F23"
    app:rowan_appDescriptionColor="#6B7280" />
```

### MoreAppsView XML attributes

All custom attributes use the `rowan_` prefix.

| Attribute | Description |
|---|---|
| `rowan_apiUrl` | JSON endpoint URL. Defaults to the SDK's Rowan Labs endpoint. |
| `rowan_listCount` | Maximum number of app rows and skeleton rows; `0` shows all available apps (three skeleton rows while loading). |
| `rowan_hideWhenEmpty` | Hide the view when no rows can be shown; default `true`. |
| `rowan_excludeOwnApp` | Exclude an item whose package matches the running app; default `true`. |
| `rowan_itemBackgroundColor` | Row background color. |
| `rowan_itemBorderColor` | Row border color. |
| `rowan_itemBorderWidth` | Row border width. |
| `rowan_itemCornerRadius` | Row corner radius. |
| `rowan_itemSpacing` | Vertical spacing between rows. |
| `rowan_itemPadding` | Inner row padding. |
| `rowan_logoSize` | App icon width and height. |
| `rowan_logoCornerRadius` | App icon corner radius. |
| `rowan_skeletonColor` | Loading placeholder color. |
| `rowan_appTitleColor` | App title color. |
| `rowan_appTitleSize` | App title text size. |
| `rowan_appDescriptionColor` | App description color. |
| `rowan_appDescriptionSize` | App description text size. |
| `rowan_appDescriptionMaxLines` | Maximum description lines; default `2`. |

### JSON response format

The endpoint may return a bare array, or an object whose array is under `apps`, `data`, `items`, `results` or `more_apps`. Each item needs a non-empty `name` (aliases: `title`, `app_name`, `appName`). Other supported fields and aliases:

| Field | Aliases | Required |
|---|---|---|
| `name` | `title`, `app_name`, `appName` | Yes |
| `description` | `desc`, `subtitle`, `short_description`, `summary` | No |
| `icon` | `logo`, `image`, `icon_url`, `iconUrl`, `logo_url`, `image_url`, `thumbnail` | No |
| `package` | `packageName`, `package_name`, `applicationId` | No |
| `url` | `link`, `store_url`, `play_url` | No |

Example:

```json
[
  {
    "name": "Super Cleaner",
    "description": "Free up space in one tap",
    "icon": "https://example.com/icons/cleaner.png",
    "package": "com.example.supercleaner",
    "url": "https://play.google.com/store/apps/details?id=com.example.supercleaner"
  }
]
```

Description and store URL are optional. Without a URL, the view uses the package name to open the Play Store. API-provided links are opened only for `http`, `https` or `market` schemes.

### MoreAppsView Java API and types

```java
MoreAppsView moreApps = findViewById(R.id.moreAppsView);
moreApps.setHeader("Authorization", "Bearer TOKEN"); // optional request header
moreApps.setOnAppClickListener(item -> false);         // true means handled here
moreApps.setOnLoadListener(new MoreAppsView.OnLoadListener() {
    @Override public void onLoaded(List<MoreAppsView.AppItem> items, boolean fromCache) { }
    @Override public void onError(Exception error) { }
});
moreApps.refresh();
```

Public methods: `setApiUrl`, `setListCount`, `setHideWhenEmpty`, `setHeader`, `setOnAppClickListener`, `setOnLoadListener` and `refresh`.

- `AppItem` contains `name`, `description`, `iconUrl`, `packageName` and `link`.
- `OnAppClickListener.onAppClick(AppItem)` returns `true` when the app handles the click; returning `false` lets the view open the store link.
- `OnLoadListener.onLoaded(List<AppItem>, boolean fromCache)` reports loaded data and whether it came from cache. `onError(Exception)` reports a load error.

## RowanUi

Use the utility class to check the SDK version or enable diagnostic Logcat output:

```java
String version = RowanUi.VERSION; // "1.0.0"
RowanUi.setDebug(true);           // Logcat tag: RowanUi
boolean debugEnabled = RowanUi.isDebug();
```

Debug logging is disabled by default. `RowanUi.setDebug(boolean)` controls it process-wide.

## Sample app

The `app` module demonstrates `NeuralOrbView`, `OceanWaveView` and `MoreAppsView`. Its `MainActivity` configures the orb's color, animation and thinking state in Java; `activity_main.xml` contains example XML configuration for all three views.

## License

OpenUI is distributed under the Apache License 2.0. See [LICENSE](LICENSE).
