# Gabay

Gabay is an Android companion app to the LifelineTech disaster preparedness web platform. It provides real-time evacuation routing and nearest evacuation center identification for users in the Philippines.

---

## Features

- **Real-time evacuation routing** — Routes the user to the nearest evacuation center using a 4-tier routing waterfall
- **Alternate center switching** — If a path is blocked, the app cycles through up to 5 alternate evacuation centers
- **ETA tracking** — Live estimated time of arrival updates every 3 seconds while navigating
- **Earthquake data** — Pulls live seismic data from the USGS API
- **Typhoon data** — Pulls latest tropical cyclone data from the JTWC RSS feed
- **Offline fallback** — Straight-line routing with bearing-calculated maneuvers when all online sources fail

---

## Routing Architecture

The app uses a 4-tier waterfall for routing, attempted in order:

```
1. Valhalla (primary)     → valhalla1.openstreetmap.de
2. Valhalla (alternate)   → valhalla.openstreetmap.de
3. OSRM                   → router.project-osrm.org
4. Straight-line fallback → always succeeds, no internet required
```

Timeouts: 5s connect / 8s read per tier.

Each `RouteInfo` object carries a `RouteSource` enum (`VALHALLA`, `OSRM`, `FALLBACK`) so the UI can reflect routing quality to the user.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Minimum SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Maps | OsmDroid 6.1.20 |
| Routing | Valhalla, OSRM (online) |
| Location | Google Play Services Location 21.3.0 |
| Networking | OkHttp 4.12.0 / Retrofit 2.9.0 |
| Architecture | MVVM (ViewModel + LiveData + Fragment) |
| Evacuation data | GeoJSON (1654+ centers, PH nationwide) |

---

## Evacuation Center Data

Evacuation centers are loaded from a bundled GeoJSON asset (`ph_evacs_merged.geojson`) containing 1654 features covering the Philippines. Both Point and Polygon geometries are supported — polygons are converted to centroids at load time.

A supplementary patch file (`qc_official_evac_centers_patch.geojson`) adds 19 verified centers from the Quezon City DRRMO official list, covering barangays including Culiat, Pasong Tamo, New Era, Baesa, and Balong-bato.

Centers are filtered to a 10km radius from the user's location and reload automatically if the user moves more than 5km from the load anchor. The app ranks and presents the 6 nearest centers, allowing the user to switch to alternates if needed.

---

## Key Files

```
app/src/main/java/com/example/gabay/
├── EvacuationRepository.kt     # Routing waterfall + GeoJSON loading
├── EvacuationViewModel.kt      # State management, ETA tracking, center ranking
├── EvacuationFragment.kt       # UI layer, map rendering, reroute handling
└── Models.kt                   # RouteInfo, EvacuationCenter, TravelMode, etc.

res/
├── layout/fragment_evacuation.xml
├── values/dimens_evacuation.xml         # Default dimensions (≥411dp devices)
└── values-sw360dp/dimens_evacuation.xml # Overrides for smaller screens (360dp)

assets/
├── ph_evacs_merged.geojson
└── qc_official_evac_centers_patch.geojson
```

---

## UI Scaling

The app uses a two-tier dimension system to handle different screen sizes:

- `res/values/dimens_evacuation.xml` — defaults targeting ~411dp screens (e.g. Poco X3 NFC)
- `res/values-sw360dp/dimens_evacuation.xml` — overrides for ~360dp screens (e.g. Poco C71)

All text sizes, icon sizes, padding, and banner margins reference `@dimen/evac_*` keys defined in both files.

---

## Known Limitations

- Routing requires an active internet connection. The straight-line fallback activates automatically when all online routing sources are unavailable, but does not follow actual roads.
- Evacuation center data is static and bundled at build time. Centers are not updated in real time.
- The app is optimized for Metro Manila / NCR usage. Routing accuracy outside this area depends on Valhalla/OSRM coverage.

---

## Future Work

- **Offline road routing** — GraphHopper embedded with a pre-built NCR graph file for true offline turn-by-turn navigation without internet dependency
- **Push alerts** — Disaster alert notifications triggered by incoming hazard data
- **Dynamic center data** — Server-synced evacuation center updates instead of bundled GeoJSON

---

## Related

- [LifelineTech Web Platform](https://github.com/) — The companion website this app extends
- [OsmDroid](https://github.com/osmdroid/osmdroid)
- [Project OSRM](http://project-osrm.org/)
- [Valhalla Routing Engine](https://valhalla.github.io/valhalla/)