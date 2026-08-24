# Music Collector — plan

Source design: Claude Design project `a1b6280a-eae4-4ab0-aab3-68ea4a303c9b`
(`Music Collector.dc.html`, with `FormatThumb.dc.html`). `ios-frame.jsx` and `support.js`
are mockup scaffolding and are **not** ported.

## Decisions

| Question | Decision |
|---|---|
| Platform | Expo mobile app + separate React web app |
| Backend | Java 25 + Spring Boot 4.1, PostgreSQL, house JWT auth |
| Metadata | MusicBrainz + Cover Art Archive, via a server-side proxy + cache |
| Cover theming | Dominant tone + luminance computed at import, persisted |
| Account | **Optional.** Full functionality with no account, local storage only |
| Sync | Per-entity, **field-level** last-write-wins |
| First sign-in | Explicit prompt: merge / keep local / keep account |
| Local store | IndexedDB (web, Dexie) + expo-sqlite (mobile), one shared interface |
| Anonymous metadata | Open endpoint, per-IP rate limit, cache-first |

## Repos

- `music-collector-backend` — this repo. Auth, sync, metadata proxy, image storage.
- `music-collector-frontend` — React + Vite + Bun web app (screens `1f`, `1g`).
- `music-collector-mobile` — Expo app (screens `1b`, `1j`, `1d`, `1e`, `2a`, `1l`).
- `music-collector-deployment` — Kustomize base + overlays, watched by ArgoCD.

## Architecture: local-first

Clients read and write **only** their local store. A background sync engine reconciles with
the server when signed in. Consequences:

- There are **no CRUD endpoints** for copies or wishlist items — `/api/v1/sync` replaces them.
- Library search, filtering and sorting are client-side. A few hundred copies filter in memory.
- Collection stats (total spent, average per copy, per-format counts) are computed client-side.
  Anonymous users need them too, and computing them twice in two places is how they drift.

What remains server-side: **auth**, **sync**, the **MusicBrainz proxy + cache**, and
**image storage**.

## Domain model

`1c`/`1g` show "other copies of this release", `1f` reads "240 copies · 197 releases", and
`2a` lists one row per release *and* format. That needs three levels — which is also how
MusicBrainz models it:

```
ReleaseGroup   the album            Bitches Brew
  └── Release  a specific edition   Columbia GP 26, 2×LP, US, 1970
        └── Copy   your item        VG+, €28, Concerto Amsterdam, 14 Aug 2026, ★★★★☆
```

`Copy` and `WishlistItem` are user-owned and synced. `ReleaseGroup` and `Release` are a
shared, cached mirror of MusicBrainz — the same rows serve every user.

Condition uses the Goldmine scale, which screen `1l` names explicitly:
`M, NM, VG_PLUS, VG, G_PLUS, G, F, P`.

## Sync: field-level LWW

Every synced entity carries a clock map beside its columns:

```
Copy { id, releaseId, condition, pricePaid, purchasedOn, purchasedAt,
       notes, rating, deletedAt,
       fieldClocks: { condition: <hlc>, pricePaid: <hlc>, ... } }
```

Merge is per field, higher clock wins, ties broken by device id. Two devices editing
different fields of the same copy both keep their edit.

Three things this forces:

1. **Hybrid logical clocks, not wall time.** With raw `Date.now()` a device with a skewed
   clock wins every conflict forever, silently. An HLC (`wallTime`, `counter`, `deviceId`)
   removes that failure mode for about forty lines of code.
2. **`notes` is the weak spot.** Field-level LWW still discards one side's paragraph whole.
   On a genuine conflict, keep both and mark it rather than pick a winner.
3. **The server is a merge participant**, not just storage: `field_clocks` JSONB per row,
   client-generated UUIDv7 primary keys, `deleted_at` tombstones on every synced table.

Protocol: `GET /api/v1/sync?since=<cursor>` pulls, `POST /api/v1/sync` pushes a batch.
Both sides fold with the same merge function.

**The merge function is written once and shared** — a small TypeScript package consumed by
web and mobile, plus a mirrored Java implementation, with committed cross-language fixtures
proving the two agree. Wharf does the same for its vault format. Divergent merge
implementations are the classic way this bug class ships.

## Anonymous mode details

- **Cover theming needs no account.** The open metadata proxy computes the dominant tone and
  relative luminance when it caches a release and returns them inline, so anonymous clients
  get themed detail screens for free. Only user-uploaded photos need client-side extraction.
- **User photos** are stored as blobs in the local store while anonymous. On sign-in they
  upload to MinIO and the blob is swapped for a URL — each image needs its own upload state,
  since a half-synced photo is otherwise invisible.
- **The metadata proxy is deliberately unauthenticated.** Abuse is bounded by a per-IP
  Bucket4j quota in front of a persistent cache, not by a login. Scanning a barcode is the
  app's best first impression and must not be gated.

## Phases

1. **Scaffold** — four repos, CI to ghcr.io, ArgoCD app, hello-world green in staging.
2. **Local-first core, no accounts at all** — `LocalStore` on both platforms, domain model,
   metadata proxy, and add → library → detail working end-to-end anonymously. The app is
   genuinely useful at the end of this phase.
3. **Accounts + sync** — auth, the shared merge function with cross-language fixtures,
   `/api/v1/sync`, the first-sign-in prompt, image upload.
4. **Remaining screens** — wishlist, profile and stats, the web sidebar layout.

Phases 2 and 3 being separable is the main dividend of local-first: sync cannot block the
app from being useful.

## Design notes carried from the deck

- Tokens: bg `#faf8f5`, dark `#141311`, ink `#191713`, accent `#a2573a` (dark variant
  `#d08a5f`). Newsreader for titles, Manrope for UI, `ui-monospace` for metadata labels.
  Icons are lucide.
- The cover-theming rule from turn 3: dominant-tone luminance below ~55% picks the dark
  chrome, otherwise the light one; the accent (stars, primary button) comes from the same
  swatch.
- `1c` and `1j` are alternates for the same screen. Build `1j`'s full-bleed dark layout —
  turn 3 explicitly evolves it — but keep `1c`'s "other copies you own" block, which `1j`
  drops and the data model needs.
