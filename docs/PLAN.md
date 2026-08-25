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

**The catalogue does not travel inside a sync batch.** A copy names a release; it never
carries one. Releases are a shared mirror of MusicBrainz and Discogs that any client may
drop and refill, and they are the same rows for everybody — putting them in a per-user
batch would replicate a public cache through a private channel. So they move over
`GET /api/v1/metadata/releases?releaseId=…`, answered from the mirror only and never from
an upstream catalogue: two hundred records arriving on a second device must not become two
hundred paced upstream lookups, and an id the mirror does not hold is simply absent rather
than a 404.

The half of that which was missing until 2026-08-25 is the client's: nothing refilled the
release cache after a pull, only the add and detail screens ever wrote to it. A browser
that signed in for the first time therefore pulled thirty copies and drew thirty untitled
placeholders — "30 copies · 1 release". The sync engine now asks its transport for the
releases the store lacks, over the **whole local collection** rather than the page it just
pulled, so a device left blank by an older build heals on its next sync instead of only on
its next new record. Hand-entered `local:` releases are never asked for: they are derived
from the copy itself.

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

## Friends (turn 15)

A handle, mutual friendship, one Friends tab holding activity and people, and per-list
visibility. Screens `15a`–`15i`. Built in three phases: identity and the graph, then the
activity feed, then the public link.

### Why any of it is server-side

The collection stays local-first, but a shelf somebody else looks at cannot be. The
viewer's device has no copy of the owner's library and must never be handed one it is not
allowed to see, so `/api/v1/profiles/**` reads the server's own rows and applies the rules
there. This is the first read path in the app that is not the owner's own data.

### Decisions

| Question | Decision |
|---|---|
| Handle vs. name | Separate. The handle is public and searched; the display name never is |
| Search | Prefix on the **handle only**, 3 characters minimum, 20 results, per-IP quota |
| Who may search | Anybody, signed in or not — a public shelf that demands a login is not public |
| Befriending | Needs an account and a handle of your own |
| Friendship | Mutual, one row, only the addressee may accept |
| Declining | Deletes the row, so the other person can ask again |
| Visibility | Three separate answers: collection, wishlist, prices |
| "Findable" | Unlisted, not private — a direct link still resolves under the visibility rules |
| Per-copy | `copies.hidden`, a mergeable field, overrides every setting above |
| Grades | Friends and up. A public page is sleeves, not condition reports |
| Feed timestamps | The device's own UTC, trusted, clamped so it can never be in the future |
| Feed contents | Only `MANUAL` adds. Imports and the first sign-in push are silent |

`VisibilityService` is the single authority: every screen, endpoint and image byte asks it
rather than comparing settings itself. The verdicts are computed live and never stamped
onto a row, which is what makes closing a shelf take effect backwards as well as forwards —
including on activity already in somebody's feed.

Photos are the same rule applied to bytes. `GET /api/v1/photos/{id}/content` was
authenticated-only and is now open, authorising per request: the owner always, a friend
when the shelf is open to friends, a signed-out stranger only when the collection is
public — and never for a copy hidden one by one. Every refusal is a 404, so the endpoint
cannot be used to confirm which photo ids exist.

### Handles

Letters, numbers and single dots, 3–30, anchored at both ends by an alphanumeric. Stored
lowercased and compared case-insensitively: `@Anna` next to an existing `@anna` would be a
convincing impersonation of it. Changes are capped at twice a year, counted from
`handle_changes` rather than a counter; re-saving the handle you already hold is free.
A released handle stays out of circulation for 180 days so the next claimant does not
inherit links and pending requests meant for whoever had it before. Reserved words cover
the app's own path segments — the public profile lives at `/@handle` and its wishlist at
`/@handle/wishlist`, so a collector called `@wishlist` would shadow a route.

### What the design draws that the data could not

`15b` matches **Friedhelm Barg `@fbarg`** against the query `frie`, which is a match on his
name rather than his handle. Handle-only search is the deliberate choice — a handle is
picked to be found by, a real name is not — so the mock data is what gives, not the rule.

### The feed

Written on the way through sync, read back through `VisibilityService` every time.

**Only the device knows why a copy exists.** An import of two hundred records and two
hundred records typed in over a fortnight reach `/api/v1/sync` in the same shape, so the
push carries an `origins` map beside the records — `MANUAL`, `CSV_IMPORT` or `FIRST_SYNC`,
keyed by copy id. Beside them rather than on them: it is the reason for one push, not a
property of the copy that has to survive or merge, and it matters exactly once, when the
server first sees the row. A copy the map does not mention says nothing. Silence is the
safe failure mode — a client too old to send this is one whose intent we cannot read, and
`useFirstSyncLogic` would otherwise announce somebody's entire collection the first time
they signed in.

Only a row the server had never seen announces anything, so an edit does not repeat it, and
a tombstone withdraws the line: a feed saying somebody added a record they have since
deleted is a claim about them that is no longer true.

**Timestamps are the device's own UTC, trusted, and clamped to never be in the future.** A
copy added on a plane and synced two days later belongs where the person put it. What that
cannot be allowed to buy is a permanent place at the top of every friend's feed, which is
all the clamp prevents. `recorded_at` is kept beside `occurred_at` so a suspicious gap
between them is still visible afterwards.

**A burst collapses at read time**, not in the stored rows — adjacent `COPY_ADDED` events
by one actor within two hours become one line, so the window can change without a
migration. Adding a copy of an album already on the wishlist is recorded as
`WISH_FULFILLED` rather than `COPY_ADDED`: "off the wishlist, onto the shelf" is the same
event told better.

**Visibility is applied on the way out.** Events are read from the actors' own rows at
request time rather than fanned out into per-viewer inboxes. That is what makes "Only me"
reach backwards — the lines do not have to be found and deleted from anywhere, they simply
stop being readable — and it means no settings change has a rewrite job that can fail
halfway.

### A shared shelf shows the owner's photos

A hand-entered copy points at no catalogue, so it has no cover art and never will --
its picture is a photograph of the record (turn 14). `SharedCopyDto.previewPhotoId` names
the copy's first photo, picked by the same rule as `copyPreviewSrc` on the clients: the
copy's own first photo wins unless it has starred the catalogue artwork instead. It is
resolved server-side in one query for the whole shelf, because the viewer has none of the
owner's photos and cannot ask the strip which one is first.

This opens no new door. `/api/v1/photos/{id}/content` was already reachable without a
session and authorises per request inside `PhotoService` -- owner always, a friend when the
shelf is open to friends, a stranger only when it is public, and never for a copy hidden
one by one. What changes is only that a viewer is now *told* which photo to ask for.

Clients fetch those bytes through the API client rather than pointing an `<img>` at the
URL: a friends-only shelf needs the viewer's token on the request, and an image tag cannot
send one.

`catalogArt: HIDDEN` is applied on the way out too (`catalogArtShown`). Taking the
archive's artwork out of a copy's images is a decision about that copy, not about the
device it was made on, and the shelf was handing the URL over regardless.

## Wishlist (turn 16)

A want list at release level: the format you want, an optional note, and the date it was
added. No price, no priority, no alerts. Screens `16a`–`16g`; `16h` (wishlist visibility)
and `16i` (a friend's wishlist) belong to the Friends turn above and are built there.

The table already existed (`V5`), so most of this turn is the rules around it rather than
new storage. What it does add is one column.

### Decisions

**The hand-built order is synced data, not a device preference.** `wishlist_items.sortIndex`
(`V21`, nullable, mergeable) is where a dragged row sits; `NULL` means "never placed by
hand", which is *not* position 0. An entry added since the last drag sorts after the placed
ones rather than jumping to the top of an order it was never part of. Which *sort* the list
is showing is the opposite — a per-device preference in the local settings store — because
the column you happen to be reading a list by is a fact about the screen in front of you,
while where you dragged a row to is a fact about the list.

**A drag renumbers the whole list densely from 0.** Not a fractional index wedged between
two neighbours: field-level last-write-wins would resolve competing fractions into an order
neither person built, whereas a dense renumber resolves into *one* of the two orders, which
is a thing somebody meant.

**"Any" matches every format on auto-removal.** This was left open by the deck. `Any` is
what the word promises, and a list that kept an entry you had explicitly marked
format-agnostic after you filed the record is a list nobody trusts to empty itself. A named
format matches only itself: wanting *Ege Bamyasi* on vinyl is not satisfied by buying the
CD, so the entry stands and you keep hunting. The copy's own `manualFormat` wins over the
catalogue's, through `copyFormat` — the rule lives in `wishSatisfiedBy` in the shared
package so the two clients cannot disagree about which entries a new copy clears.

**Removal is triggered by the add, not by the store.** Every path that files a copy calls
`useSatisfyWishes`; the store does not do it on `putCopy`. The store is where records are
written, not where product decisions live, and a sync pulling somebody else's copy in must
not silently rewrite this device's wishlist.

**A wish can be for a record no catalogue has.** Its `albumId` is `local:<uuid>`, the same
prefix a hand-entered copy uses (turn 14). Nothing can look it up, which is exactly right:
the entry makes no claim about the archive, and it can therefore never be auto-matched.

**Three wanted formats, not five.** `VINYL | CD | CASSETTE | null`. `DIGITAL` and `OTHER`
are formats a *copy* can be, but nobody hunts for a download — a wishlist is a list you
keep so you remember at the shop. A wish taken from a release that is neither vinyl, CD nor
tape becomes "any" (`asWishFormat`) rather than an unpickable fourth chip.

**"I found a copy" opens the add flow with the wish's search run, not a fixed pressing.**
A wish names an album; which pressing you found is still yours to pick. The entry stays on
the list throughout — backing out of the add flow costs nothing — and leaves only once a
copy exists, through the same automatic removal as any other add. That is also why there is
no separate "take it off the wishlist" toggle: the undoable line is the one mechanism, and
a second one would be a second answer to the same question.

**The artwork is resolved by the server, not carried by the entry.** A wish names an album,
and a cover belongs to a pressing, so an entry has no picture of its own —
`GET /api/v1/metadata/albums/covers` answers a whole screenful at once from the mirror: the
cover of a pressing known to have art first, an unprobed one next, never one known to have
none. It calls no catalogue, because a list of thirty rows must not become thirty upstream
lookups, and the mirror already holds the pressing the entry was created from. Storing the
URL on the entry instead was the obvious alternative and is wrong twice: it would be a
synced field for something that is a shared cache rather than part of anybody's collection,
and it would freeze the answer for entries added before the archive had one.

Deriving the URL on the device was the other alternative, and search is Discogs-first — the
Cover Art Archive resolves a front cover per MusicBrainz release group, but Discogs
publishes no per-album image, so most entries would have got nothing. An unmirrored Discogs
album therefore answers null and the client draws its format silhouette, which is the same
thing it does for the four releases in ten whose cover URL 404s.

## Legal layer (turn 17)

The German legal layer: an Impressum, a Datenschutzerklärung and Nutzungsbedingungen, a
consent step at sign-up, and DSGVO self-service that does not go through an e-mail. The
documents carry a DE/EN switch of their own; the app's UI language stays a separate
setting, because which version binds you is a legal question and which language you read
menus in is a preference.

**The documents live in the shared package, not in each client.** They are the one kind of
content where the two apps disagreeing is a real failure: § 5 DDG wants one identifiable
provider, and an Impressum that reads differently on the website and in the app is worse
than none. `legal/` exports the three documents as structured sections in both languages,
plus the operator constants — every screen that prints an address, a controller or a contact
reads them from there rather than typing them again. The German text is the binding
original; the English is a courtesy translation of it, and says so on every page.

**The server owns the document versions, not the client.** A consent record has to survive
the document being rewritten, so `user_consents` (V22) stores which document, in which
version, was accepted when — and the version is stamped from `ConsentDocument` on the server
rather than taken from the request. A client three releases old can only say *that* the
boxes were ticked; what it ticked is a fact about the server at that moment.

Two ticks on the screen, three rows in the record: the agreement covers the terms and the
privacy policy together, the age confirmation stands alone, and each has to be shown on its
own afterwards. Both are `@NotNull @AssertTrue` on `RegisterRequest` — required rather than
merely recorded, because a box the server would accept unticked is decoration. `@NotNull`
matters as much as `@AssertTrue`: Bean Validation treats null as satisfying `@AssertTrue`,
so a client that omits the fields would otherwise sail through. **This makes older clients
unable to register** — including the iOS build already in the store, which needs an update
before its sign-up screen works again. Signing in with an existing account is unaffected.

The OAuth path records the same three consents when it creates an account. There is no form
to put tick boxes in, so the provider buttons carry the legal notice line instead; the
agreement is the same, so the record is the same.

**Consent rows cascade with the account.** The deletion screen promises everything goes, and
a proof-of-consent row still names the person it is about. Keeping one to prove a consent
that no longer has a subject would make that promise false, so the privacy policy says
plainly that the records go with the account.

**The export is two exports, and which one you get depends on whether you have an account.**
`GET /api/v1/account/export` is the Art. 15 and Art. 20 answer for what the *server* holds:
account, consents, sharing settings, copies, wishlist, photo metadata, friendships and which
sign-in providers are linked. It reuses the sync DTOs on purpose — portability means the file
can be read back, and the shapes the app already speaks are the ones that can be. Photo bytes
are not inlined; each photo's metadata carries the URL they live at. A device with no account
has nothing on the server at all, so it exports from its own local store instead, which is
also why the CSV export beside it stays client-built.

Rectification and erasure needed no new endpoints: the name is already `PATCH /auth/me`,
withdrawing sharing consent is the Sharing screen saved with everything private, and deletion
is `DELETE /auth/me` as before — the typed-`LÖSCHEN` confirmation is a client-side gate on it,
not a second endpoint.
