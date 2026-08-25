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
3. **The server is a merge participant**, not just storage: field clocks stored per row,
   client-generated ids, `deleted_at` tombstones.

Protocol: `GET /api/v1/sync?since=<cursor>` pulls, `POST /api/v1/sync` pushes a batch.
Both sides fold with the same merge function. The cursor is a **sequence**, not a
timestamp — a timestamp cursor would suffer exactly the clock skew that motivated the
hybrid logical clocks in the first place.

Two traps found by testing, both of which lose data:

- Pulling must look the local record up **including tombstones**. With a
  tombstone-hiding lookup, a deleted copy looks like one the client has never seen, the
  server's live version is adopted wholesale, and every delete comes back on the next sync.
- A delete is an ordinary **stamped** write of a tombstone. An unstamped one loses every
  merge, so the store deliberately exposes no unclocked delete at all.

**The merge is written once and shared.** `merge-fixture.json` is hand-authored and
committed to all three repositories; the TypeScript and Java implementations are each
tested against it, three ways per case — the expected result, commutativity, and
idempotence. Hand-authored rather than generated, so no implementation's bugs can quietly
become the contract.

The shared files (`hlc.ts`, `types.ts`, `copyWrites.ts`, `merge.ts`, `LocalStore.ts`,
`theme.ts`, `syncEngine.ts`) are currently **duplicated** between the two frontends with
MIRROR headers saying to change them together. Extracting them into a real package is
outstanding work, not a decision.

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

1. **Scaffold** — four repos, CI to ghcr.io, ArgoCD app, hello-world green in staging. *Done.*
2. **Local-first core, no accounts at all** — `LocalStore` on both platforms, domain model,
   metadata proxy, and add → library → detail working end-to-end anonymously. *Done.*
3. **Accounts + sync** — auth, the shared merge with cross-language fixtures,
   `/api/v1/sync`, the first-sign-in prompt. *Done, except image upload — there is no photo
   capture yet to upload, so it moves to the phase that adds one.*
4. **Remaining screens** — wishlist, profile and stats, the web sidebar layout, and
   surfacing `notesConflict` in the detail screens. *Done.*
5. **Sleeve photos** — camera capture and picker on mobile, a file picker on the web,
   upload to MinIO, and per-image upload state. *Done.* The whole of the original design
   is now built.

Photos follow the same bargain as the collection: one taken with no account stays on the
device and still renders. The bytes never travel inside a sync batch — that would make
every sync as slow as the largest photo in it — so they move over dedicated endpoints and
the sync payload carries only metadata. Bytes upload *before* the metadata is pushed,
because a photo record with no storage key is one other devices can see but never fetch.

Storage is the shared cluster MinIO with a bucket and a scoped user per environment,
rather than a StatefulSet per namespace. Two more stateful workloads on a single node is a
poor trade for isolation that credentials already provide.

`WishlistItem` carries field clocks exactly like `Copy`, and both push in one request
under a single pending set — a session that added a record and wished for another must
not race two calls.

The pull cursor must never advance past a record the response did not include. Clamping
it unconditionally to the lower of the two record kinds strands the other kind on the
server while `hasMore` claims the client is up to date.

Phases 2 and 3 being separable is the main dividend of local-first: sync cannot block the
app from being useful.

## Manual entry (turn 14)

A copy of a pressing no catalogue has — a bootleg, a test press, a tape somebody made.
Screens `14a` (mobile) and `14b` (web, the fourth tab of the add sheet).

Its release facts live **on the copy**, as six mergeable fields (`manualTitle`,
`manualArtist`, `manualYear`, `manualLabel`, `manualCatalogNumber`, `manualFormat`), and
its `releaseId` is `local:<the copy's own id>`.

Three decisions, each of which the obvious alternative gets wrong:

- **Not in the `releases` mirror.** That table is a shared cache of MusicBrainz and
  Discogs, keyed by their ids and dropped freely; a pressing only one person has ever seen
  is user data that has to sync and has to survive the cache being cleared.
- **Six fields, not one blob.** They merge under their own clocks, so correcting the year
  on the phone and the label on the laptop keeps both — the same reason every other field
  on a copy is separate.
- **The release id is the copy's own id.** A second generated uuid would make it possible
  to have a manual release nobody owns, or a copy pointing at a release the device has
  never heard of. With the copy id inside it, any device holding the copy resolves the
  release; nothing is cached, `manualRelease(copy)` derives it on read.

Both stores resolve `local:` ids through that function rather than the mirror, so every
screen keeps reading a `Release` and none of them needs a branch. The CSV export already
carried the human-readable columns; the importer now reads them back, so a hand-entered
copy survives a round trip instead of being skipped.

## Changing the format of a copy

The catalogue answers for the *pressing*, but a collection is allowed to hold a cassette
of a record MusicBrainz only lists as vinyl. `manualFormat` is therefore the one manual
field a **matched** copy may also carry: set, it overrides the release's format; null, the
archive's answer stands. Picking the catalogue's own format again clears it.

The alternative — re-matching the copy to a release of the right format — throws away its
photos, grades, price and notes to fix one word, and for a format nobody catalogued there
is no release to point at.

No migration: the field, its clock and its sync contract already existed for hand-entered
copies. Everything that shows, filters or counts a copy's format goes through
`copyFormat(copy, release)` in the shared package, so the shelf chip, its count, the badge
and the silhouette cannot disagree about the same copy. Both stores read it the same way
(the mobile SQL coalesces the copy's answer *first*), and the CSV round trip carries an
override back in — except a blank format column, which parses as `OTHER` and would
otherwise mark every row of a foreign file Other.

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
