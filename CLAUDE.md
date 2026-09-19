# Ardoise

Unofficial native Android client for [Spliit](https://spliit.app). Upstream
ships `spliit-web` (Next.js) and `spliit-ios` (SwiftUI); there is **no shared
cross-platform codebase**.

**When a behaviour is ambiguous, check what the web or iOS client does before
inventing something.** Two clients that disagree show two different balances to
people in the same group. That is the failure this codebase guards against.

**The app is Ardoise; the service is Spliit.** `ArdoiseApplication`, `ArdoiseApp`,
`ArdoiseTheme`, `ArdoiseTextField` are this app. `SpliitApi`, `SpliitResult`,
`SpliitError`, `SpliitCache`, `SpliitDatabase` name Spliit's API and data, and
keep those names. So do user-facing strings: "Join X on Spliit" stays.

**Never rename the stored files**: `spliit_settings` (DataStore), `spliit-cache`
and `spliit-outbox` hold the group ids and any queued expense. Renaming orphans
both.

## Stack and modules

Kotlin, Compose, Material 3, minSdk 26. Coroutines + Flow, Ktor +
kotlinx.serialization. Versions in `gradle/libs.versions.toml`.

    :app    Compose UI, navigation, ViewModels, DI
    :core   Domain models, settlement, formatting. Pure JVM, MUST NOT
            depend on the Android SDK
    :api    tRPC client, superjson, DTOs. Pure JVM; Ktor client, no engine

One-way: `:app` → `:api` → `:core`, compiler-enforced via `kotlin-jvm`. Never add
an Android dependency to `:core` for convenience — extract instead.

## Commands

    ./gradlew assembleDebug             # build
    ./gradlew testDebugUnitTest         # :app JVM tests
    ./gradlew test                      # :core and :api JVM tests
    ./gradlew connectedDebugAndroidTest # instrumented, needs a device
    ./gradlew lint
    ./gradlew spotlessCheck / spotlessApply

All but `connectedDebugAndroidTest` run without an emulator. Run them before
declaring work done.

## Formatting

ktlint through Spotless, plus **compose-rules** (stateless composables, a
`Modifier` parameter on anything that emits, no lambda captured by a restarting
effect).

Two files decide what runs: `.editorconfig` (what Android Studio reads) and
`ktlintRules` in `build.gradle.kts`. **Spotless's own defaults beat the file**, so
a rule left to `.editorconfig` alone is not in force — hence the trailing-comma
settings appearing there. Most of that map switches rules *off*: the formatter
stops style drifting, it does not re-wrap code laid out for reading.

## API

tRPC with the **superjson** transformer at `{baseURL}api/trpc`. Nothing in the
data model has a REST route.

- Queries `GET …/{procedure}?input=<envelope>`, mutations `POST` with the
  envelope as body. Both **unbatched** — batching is a different wire format.
- **Decoding ignores `meta.values`** (models are typed). **Encoding must emit
  it**: the server rebuilds real `Date`s from the annotations before validating.

`api.Procedures` is the only place paths are written. Mapped:
`groups.{get,getDetails,list,create,update}`,
`groups.expenses.{list,get,create,update,delete}`,
`groups.balances.{list,forUser}`, `groups.activities.list`, `categories.list`.
**Not mapped:** `groups.stats.*`.

`categories.list` needs neither input nor group, which is what makes it the probe
for "is this address a Spliit server".

When adding a procedure, read it *and* the Prisma `select` behind it in
`src/lib/api.ts`. Selects differ between endpoints returning the same entity —
`expenses.list` nests the participant in each `paidFor` row, `expenses.get`
returns a bare id.

Six facts that have already bitten:

- `Group.currency` is a **display symbol** (free text). `Group.currencyCode` is
  the ISO code and is **nullable** on older groups. Only the code implies scale.
- Every date is superjson-annotated **except** `groups.list.createdAt`.
- `Expense.conversionRate` is a Prisma `Decimal` sent as a string. Keep it text,
  never a `Double`.
- Mutations are **not idempotent**. A retried `expenses.create` creates a second
  expense. Never retry one automatically.
- `paidFor` has no `ORDER BY`. Anything showing participants in an order imposes
  it, normally the group's.
- An expense update **replaces its documents** — upstream deletes every one whose
  id is absent from the payload.

## Money

Amounts are **integers in the currency's minor unit**; the group's ISO code
decides what that means. `1234` is `12.34` in euros and `¥1,234` in yen.

- Never `Float`/`Double` for an amount, anywhere.
- Never assume two decimals. Resolve scale from the group's code.
- **A share's meaning depends on split mode** (table on `PaidFor` in `:core`):
  ×100 for `BY_SHARES` and `BY_PERCENTAGE`, raw minor units for `BY_AMOUNT`,
  ignored for `EVENLY`. The server rescales only shares sent as *strings*; this
  client sends numbers, so a wrong scale is stored as given.

Getting this wrong is a silently wrong balance. New arithmetic on amounts gets a
unit test.

### Formatting money

`core.currency` resolves scale and formats through `BigDecimal`, never a
`Double`.

**Do not use `Currency.getDefaultFractionDigits()`** — the JDK says HUF, COP and
IDR have two decimals; Spliit says zero. `GroupCurrency.ZERO_DECIMAL_CODES`
mirrors upstream's `currency-data.json` and is the authority. Unknown codes fall
back to two decimals, as upstream does.

A group with no ISO code is formatted as euros with the euro sign swapped for its
symbol — upstream's trick, and why the symbol lands where the locale puts one.

**Everything read goes through `format`; everything typed goes back through
`parse` in the same locale** — `de-DE` reads `12.34` as twelve *thousand*.
`formatPlain` seeds an editable field, `parse` reads it back; grouping is off
there so the next parse cannot trip on a separator nobody typed.
`formatForEditing`/`parseDecimal` are the same pair for split-editor shares.

Screens ask `ui/currentLocale()`, never `Locale.getDefault()`.

### Another currency

An expense can be paid in a currency the group is not counted in. Spliit stores
the group-currency `amount` (what balances are built from) plus `originalAmount`,
`originalCurrency` and `conversionRate`.

**The rate reads "1 original = rate group"** — the expense's own currency is the
base, matching upstream's `convertToGroupCurrency` (`originalAmount * rate`) and
its rate source. Storing the reciprocal makes the same expense read differently
in the web client. `core.currency.convertToGroupCurrency` is the port: minor units
up to major, multiplied, back down to the group's scale, because the two
currencies can have different scales.

**`ExchangeRates` is not Spliit's API** — it is
[Frankfurter](https://frankfurter.dev), which upstream's `useCurrencyRate` also
queries, so both clients fill the field from one source. It asks for the rate **on
the expense's date**, is the only host the app talks to that is not the chosen
instance, and is told nothing but a date and two codes. Asked once when a foreign
currency is picked, then only on request.

On the form **`amount` is the group's number** (`enteredAmount` is what was
typed), so split sums and the recurrence preview need no idea a conversion
happened. A group with no ISO code cannot convert, so the currency chip is flat.
The split tile does not say "€19.29 each" because largest-remainder gives one
participant a different cent.

## Dates

`ui/components/Dates.kt`. `rememberDateFormatter` takes an ICU **skeleton** —
which fields to show — never a pattern, which is also an order: `yMMMMd` gets
`September 6, 2026` in `en-US` and `2026年9月6日` in `ja-JP`. `j` is the hour, so
12/24 is the locale's. Four in use: `FullDate`, `ShortDate`, `DayHeading`,
`TimeOfDay`.

## Repositories

`android.data` sits between `SpliitApi` and the ViewModels. **Reads are `Flow`,
mutations are `suspend`** returning a `SpliitResult`.

A read is **the cache, then the network** (`SpliitCache.cachedThenFresh`): Room's
rows go out at once, a refresh follows, and a failure arrives *after* the cached
value — which is what lets a screen show rows under an "Offline" banner instead
of an empty state.

**An empty cache is not an answer.** "Nothing stored" and "the server says
nothing" look identical, so an empty cache emits nothing until a refresh has run.
`isCached` says what empty means per read.

Room's DAO `Flow`s wake on any write to a table they read. Room cannot know that
changing an *expense* invalidates the *balances* and the *activity log* —
`SpliitCache.refreshAfterExpenseChange` is that, and `balanceChanges` is the same
for `groups.balances.forUser`, the one read with no rows of its own.

- `groups()` is driven by the stored ids, so remembering or forgetting a group
  re-emits on its own.
- `GroupRepository.create` **remembers the new id as part of creating** — the id
  is the only way back in.
- `ExpenseRepository.create` returns `Created.Sent` or `Created.Queued`.
- Forgetting a group takes its cached contents (`CacheDao.forget`, one
  transaction) and its queued expenses. Deliberately **not** `ON DELETE
  CASCADE`: the feed and the group are fetched concurrently, so a foreign key
  turns that race into a crash.
- `ActivityRepository` is read-only; the server writes the log.

"Try again" is `ui/Retry`, not a repository method: a read refreshes once per
subscription, so retrying means subscribing again — one screen asking for itself,
never an announcement.

**Pull to refresh and the banner's button are one action**: both call
`Retry.again`, and both raise the same indicator. When that indicator comes down
cannot be read off the emissions: a refresh that changes nothing writes identical rows, so
`cachedThenFresh` drops the repeat and the answer never arrives. `RefreshScope`
is the signal, **carried in the coroutine context** so that what it counts is one
subscription — `refreshAfterExpenseChange` runs with no scope in context and
moves nothing. It cannot live in `SpliitCache` either: an empty group list, a
search and `balances.forUser` never reach `cachedThenFresh` and would hang or
retract early, so `Retry.track` counts an emission as an answer too.

`SpliitApi` is an interface so repositories test against a fake that states its
answers outright, failures included.

## Theme

`ui/theme` holds **two schemes; the dark one is fixed, value for value**.
`ArdoiseTheme(darkTheme = …)` picks; settings offers light, dark or system.

**The light scheme is derived, not picked by eye.** M3 tone is CIELAB L*, so tones
the dark scheme does not use were interpolated from the ones it does
(`tools/tones.py`, held-out tones within ΔE 1.6). No dynamic colour — Material You
would replace the palette rather than turn it over.

Two things the scheme cannot carry: **`WarningColors`** (M3 has no warning role)
and **`groupTiles`**, both read from `MaterialTheme.colorScheme` at the call site.
`LocalIsDarkTheme` tells them which way up they are.

**System bars follow the app, not the system**, via `SystemBarStyle.auto(…) { dark }`.
**The window background cannot**: it is painted before DataStore is read, so
`values/colors.xml` and `values-night/` are the best available and a user whose
choice opposes their system sees one frame of the other.

## Text fields

**Use `ui/components/Fields.kt`, not `OutlinedTextField`.** The label stays in the
border at all times with the placeholder inside; Material's drops it into an empty
field, and the overload that does not takes a `TextFieldState`, undoing "state
hoisted to the ViewModel".

- `ArdoiseTextField` for typed values. `labelBackground` is what the label punches
  out of — a field inside a card must be told that card's colour.
- `ArdoisePickerField` for chosen values. Not a read-only text field, which would
  still take focus and show a cursor.
- `InlineNameField` for a participant's name, edited in place, no padding.

`FieldLabelOverhang` is how far the label rises above the field's box; it is drawn
with an `offset`, which does not measure, so a caller with something directly
above adds it back.

Two layout rules with tests: an `IconButton` is 48dp whatever is in it, so a bare
`Icon` beside one needs the same box; and nothing between a name and its caption
means no gap.

Icons are Material Symbols Outlined in `res/drawable/ic_*.xml`. **Do not
hand-edit them** — add the name to `tools/icons.py` and run it.

## Screens

`android.ui`. Each screen is a stateless composable plus a `…Route` wrapper that
gets the ViewModel from Koin, so it can be tested and previewed without the graph.

Errors never reach a screen as an exception message: `SpliitError` and
`BaseUrl.Reason` become text in `ui/ErrorMessages.kt`.

**One `Scaffold` per screen, never nested.** The `NavHost` has none. Two apply the
status bar inset twice.

**The group's three views are state, not destinations.** `GroupHost` holds which
of Expenses · Balances · Activity is showing and passes `GroupTabs` into that
screen's own `Scaffold` as a `bottomBar` slot. On the back stack, back would walk
through whichever tabs had been looked at; wrapping rather than slotting would
nest two `Scaffold`s.

**Never pass a suspending call as an argument to `copy()`.** Kotlin evaluates the
receiver first, so it reads the state, suspends, then writes the stale copy back.
Use a local or `_state.update { }`.

About carries the links a sideloaded app has no other route to: source, issue
tracker, and Spliit's Open Collective (which pays for the instance, not this app).
**No open-source licences screen** — the app ships from GitHub, so the dependency
list and licences are already in the repository the first row links to.

### Forms

**A save checks every rule and reports all of them.** State carries `errors: Set<…>`;
editing a field clears that field's complaint alone. A rule depending on another is
checked only when the first held (split sums are not compared against a missing
amount), and the split editor opens itself only when the sums are the *whole* of
what is wrong.

**An error goes on the field it is about**, reason underneath as `supportingText`.
Only `Failed` — the server refusing the whole thing — sits at the foot.

Participant errors carry an **index** against the rows **as typed**, not the
filtered list the server is sent: blank rows are dropped before saving, so a
position in the shorter list marks an innocent row. Renaming clears that row's
mark; **removing a row drops every mark**, because everything under it shifted.

**A refused save scrolls to the first wrong field**, via `FormScroller`. Positions
are measured in the **root** and offset against the container, because fields sit
several layouts deep. `fieldOrder` gives errors the order a `Set` lacks;
`refusedSaves` is a counter, so saving twice unchanged scrolls twice.

**Adding a participant puts the cursor in the new row.** `participantsAdded` is the
counter behind it, because two blank rows look identical. The `FocusRequester` goes
on the field, not the row.

**A half-filled form survives the process being killed.** The expense form and
create-group write a `@Parcelize` draft (`ExpenseFormDraft`, `CreateGroupDraft`)
into a `SavedStateHandle`:

- **Only what the user authored.** Group, participants and categories are fetched
  again; a stale copy would show names the group no longer has. Nothing transient
  either — a reopened dialog or a complaint about a save that never happened is
  the app inventing a state the user never left it in.
- **The draft goes on top of what the server says**, after the form's load, being
  the newer of the two. Anyone it names is dropped if no longer in the group: a
  share against an unknown id is a `BAD_REQUEST`, a payer is worse. Saving starts
  only once the load finished, or the blank form overwrites the draft.
- **This is not the outbox.** Nothing in a draft has been saved, and it lives only
  as long as the task. An expense saved with no network goes to `ExpenseOutbox`.

Koin builds the `SavedStateHandle` from the ViewModel's `CreationExtras`, not the
graph, so it is asked for as `params.get()`. `AppModuleTest`'s `verify()` cannot
see that, hence `SavedStateInjectionTest`.

## The expense form

**This is where the money rules bite** — read "Money" first. The amount is typed
in major units and read back through the group's `GroupCurrency`, and a share
means something different in every split mode. Both sums are checked before
saving, so the user sees which numbers are wrong instead of a `BAD_REQUEST`.

Changing split mode carries the **people** over, not the numbers; everyone kept
gets an even value by largest remainder, so three people come to exactly 10000.

**The rows nobody has typed in absorb whatever the required total leaves**, so a
percentage split adds to 100 without anyone doing the arithmetic. `splitFree` is
the set of those rows; typing in one takes it out, and an expense read back from
the server starts with the set empty — those numbers are somebody's, not the
editor's to move. Once every row is typed a wrong total is reported rather than
silently corrected, and a free row left nothing drops out of the expense, which
is what typing 0 already means.

`activeParticipantId` and `paidById` are different: the first is who you are and
marks "(you)", the second is who paid and only *starts* there.

**A save is never retried on its own** because `create` is not idempotent: one
that cannot reach the server is queued, one the server refuses stays on the form.
`pendingId` opens a queued expense, and saving **replaces** that entry.

The form sends no `documents`; `ExpenseRepository.update` reads the set back and
carries it over, or an edit here would delete an attachment added on the web.

## Balances and settling up

Balances come from `groups.balances.list` rather than local arithmetic, because
`:core`'s `settle` needs every expense in hand. These are the server's *public*
balances, so app and web client always agree.

Settling up writes a **reimbursement expense**; there is no payments table.
**Nothing is written until the user confirms**: "Mark as paid" opens the expense
form pre-filled, as upstream's reimbursement link does, because `create` is not
idempotent and nothing undoes it.

- The title comes from `strings.xml`, not the two names, so both clients read the
  same.
- `ReimbursementPrefill` rides the route as optional query arguments and is
  **checked against the group**; one naming somebody removed falls back to a blank
  form. Its title resolves in the `NavHost`, since a ViewModel has no `Context`.
- **The suggestions are a chain**: what a payment clears is read by walking the
  list and taking each amount off, because each assumes the ones above were made.

## Totals

`ui/totals` over `core.stats`, computed locally rather than through
`groups.stats.overview`: same arithmetic, and `:core` is where it can be tested
beside the settlement maths it must not contradict. Cost is one large page; if a
group outgrows it, that procedure is the way out.

**Reimbursements are excluded from every figure.** `perPerson` and `perDayEach`
round half away from zero; they are display aggregates nothing downstream reads.

## Searching and filtering

The text filter is the server's (`groups.expenses.list`'s `filter`); category,
payer and date chips are local, because the procedure only filters on text. Hence
**setting a chip refetches the whole group** — the count above the feed is across
the group, not one page — and a search is not cached.

## Offline

Every read works from a **Room cache** (`data/local`), so the group list, a feed,
its balances and its activity log are all there on a cold start.

`ui/components/OfflineBanner.kt` sits under the app bar of every reading screen and
**goes up for two reasons**: a read that failed (saying what went wrong), and the
platform reporting no network. Without the second, a screen open when the phone
loses signal sits there looking current. That is the *only* thing the platform's
answer is read for.

**"No network" means no `NET_CAPABILITY_INTERNET`, never `NET_CAPABILITY_VALIDATED`.**
Validation comes from a connectivity check the user can switch off — GrapheneOS
ships a toggle for it — and a working network is then never marked validated, so
the banner sat over a live connection. A captive portal or a half-open wi-fi is
covered by the other reason: the read fails, and says why.

**Nothing in the callbacks asks the manager anything.** Inside `onLost` it still
reports the network being torn down as usable, so flight mode read as online and
no banner went up at all. `AndroidConnectivity` tracks the network the callbacks
are about and uses the capabilities it is handed.

**Going offline is only believed once it lasts a second.** Handing over from
wi-fi to mobile loses the old network ~50ms before the new one arrives, measured;
a banner for that is a red flash over a phone that was connected throughout.
Coming back is reported at once.

**The cache is never a source of truth.** Every row came from the server and is
replaced by the next fetch, which is why the database uses
`fallbackToDestructiveMigration`. What cannot be re-downloaded — group ids, which
participant you are — lives in DataStore.

- **Cached**: group list, a group and participants, the feed, one expense in full,
  balances and suggested payments, the activity log, categories.
- **Not cached**: a **search** (a question about the whole group, whose answer
  would overwrite the feed with a subset of itself), `groups.getDetails`, and
  `groups.balances.forUser` (it spans every group).

Three consequences:

- **A page of summaries must not overwrite what only a `get` knows.**
  `expenses.list` has no `notes` and no `conversionRate`, so
  `ExpenseDao.replacePage` carries both over.
- **A cached expense carries its category**, or a cached feed loses every icon
  until something fetches the category list.
- **`hasMore` is not cached**. A full page stands in for it.

**A refresh replaces the window the page describes and leaves older rows alone.**
Rows the page reached past and did not return were deleted upstream; older rows
are outside the window. With `hasMore == false` the page *is* the group. Kept rows
are renumbered to sit after it.

**Paging grows the page rather than fetching the next one** (feed and activity
log). The cache stores "the newest N, replaced", which is what makes an expense
deleted elsewhere disappear here; merging appended pages would keep it. The cost
is re-fetching the visible list. When that stops being acceptable it becomes a
Paging 3 `PagingSource`, which needs the cache to learn about page boundaries.

**Nothing in the cache is ever written locally first**: no dirty flag, no pending
column, no conflict to resolve.

## The outbox

An expense saved with no network is **queued, not lost** (`data/ExpenseOutbox.kt`,
stored by `data/local/outbox/Outbox.kt`).

**A second Room database, `spliit-outbox`, and durable**: real migrations,
`exportSchema`, no destructive fallback. That is the whole reason for two
databases — the cache's destructive migration would throw away the one thing the
server has never seen.

**Only a network failure is queued.** A `BAD_REQUEST` is the server refusing the
expense itself.

**The queue is visible and the user drives it.** Queued expenses are their own
section at the top of the feed, **dashed** in the error role, since a solid card
would read as one more thing in the list. A row opens the form to edit or discard.
That visibility is what makes retrying safe: `create` has no idempotency key, so a
send whose answer was lost cannot be told from one that never arrived, and nothing
retries silently where a duplicate would go unnoticed.

`ExpenseOutbox.alreadySent` narrows that window before a *re*-send by looking for
an expense with the same title, amount, date and payer created since this one was
queued. It can drop the second of two genuinely identical expenses — a missing
expense the user can see and re-add, rather than a duplicate skewing a balance
nobody rechecks. **If the server ever accepts a client-supplied expense id, delete
this function.**

Three rules, each tested: **oldest first, and a network failure stops the flush**;
**a rejected expense does not stop the ones behind it**; **one flush at a time**,
guarded by a `Mutex`.

`Connectivity` is the trigger — `registerDefaultNetworkCallback`, the "it is back"
edge only, conflated because a device moving from mobile to wi-fi announces both.
`AppViewModel` flushes on it.

Only expenses are queued. Extending this needs an answer to what a conflicting
server state means for each mutation.

## Category icons

`ui/components/CategoryIcons.kt` gives **every category its own icon**, keyed on
`grouping/name` exactly as the web client does. Glyphs are the Material Symbols
nearest its Lucide ones, since a second icon set in the feed would read as a
mistake.

**Distinct on purpose, including where upstream reuses one**; `CategoryIconsTest`
fails if two categories share a glyph, the sole exception being
`Uncategorized/General`. The tint comes from the grouping, so a run of transport
expenses reads as a run.

Sheet tiles have a **fixed height** (`CategoryTileHeight`), and `CategoryTileTest`
asserts the constant rather than one tile against another: names wrap at different
widths, so two tiles agreeing proves nothing.

## Recurring expenses

**The rule set is the server's: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`.** A yearly
rule comes back a `BAD_REQUEST`.

"Next occurrences" is a **prediction** — the server creates the copies — so
`core.recurrence.nextOccurrence` is a port of upstream's `calculateNextDate`, not
plausible date arithmetic. Monthly is the subtle part: upstream walks the day of
the month *down* until it exists, so 31 January becomes 28 February and stays on
the 28th thereafter. `LocalDate.plusMonths` clamps the same way; `RecurrenceTest`
pins both.

## Which one is you

Every group asks which participant the user is, and **the answer never leaves the
device**: Spliit has no accounts, so there is nobody to tell. It lives in
`GroupPreferences.activeParticipantId`, keyed by group, and preselects "Paid by",
makes a balance "yours", and fills the group card's standing.

Asked on the create-group form (as an *index*, since participants have no ids
until the server assigns them, so removing a row must move the selection), in
group settings, and by the identity prompt. All three write the one preference.

**Saved the moment it is chosen, not on Save** — it cannot fail and there is
nothing to roll back. Same for default currency, language and theme; a test
asserts no `groups.update` goes out with it.

## Settings

**"Currency for new groups" is about this device**, not any group: it decides
which option the create-group form *opens* on, nothing else. It lives in DataStore
beside the base URL, not per server, since which currency someone splits in
follows them.

Until set it is the device's own currency (`GroupCurrency.defaultCodeFor`),
falling back to `FALLBACK_CODE` (EUR) when the locale names no country or one
Spliit does not list. `DefaultCurrencyTest` walks every JVM locale to prove the
answer is always in `SUPPORTED_CODES`. Only the *code* comes from
`java.util.Currency`.

**A currency picked on the form is never overwritten by the stored one** — the
preference arrives asynchronously, and `currencyChosen` guards it.

## Language

Ships **English and French**; settings offers the device's language, English or
French.

**"System default" is not a third translation.** Android resolves `values-fr` for a
French device and `values` for everything else, so "fall back to English" is the
platform doing what it already does — and why an unknown stored tag reads back as
`System`.

Applying a choice is by hand, because **per-app language is API 33 and minSdk is
26**. `ui/AppLanguage.kt` does it in two halves, both needed:

- **The composition reads localised resources** — a `ContextWrapper` around the
  activity, not the bare context `createConfigurationContext` returns, because
  screens start activities and bind a camera through `LocalContext`.
- **The process default locale follows**, because `:core` formats money with
  `Locale.getDefault()`. Set *before* the content composes; afterwards leaves the
  first frame in the old language.

The app draws nothing until the language is known; `AppViewModel` observes the
preference, so there is no restart. The preference is a BCP-47 tag in DataStore,
**absent meaning "follow the device"**.

Two lint warnings are deliberate: `MissingQuantity` (French `many` applies from a
million upwards) and `AppBundleLocaleChanges` (this ships an APK with both
languages).

## Updating a group

`groups.update` takes the **whole** group, so `GroupSettingsViewModel` carries the
currency symbol and ISO code through untouched. Dropping them silently rewrites
the scale of every amount in the group.

A participant who is on an expense cannot be removed. The server refuses, and so
does this, with the reason rather than a relayed `BAD_REQUEST`.
`groups.getDetails` exists to say which ones those are.

The invite QR is a real encoding of the group URL through `com.google.zxing:core`:
the zxing-cpp wrapper reads and does not write.

## Scanning a QR code

Spliit encodes the plain group URL, so `GroupLink.parse` handles a scan and a
paste identically.

**zxing-cpp, not ML Kit.** Scanning is a core way into the app and ML Kit is
closed source; keeping this open (Apache-2.0) leaves the app buildable from
source. `io.github.zxing-cpp:android` is the maintained rewrite;
`zxing-android-embedded` stopped in 2021 and MediaPipe has no barcode task.

`ui/scan/QrViewfinder.kt` fills whatever box it is given: CameraX `Preview` plus
`ImageAnalysis` on `STRATEGY_KEEP_ONLY_LATEST`, since decoding is slower than the
frame rate and a stale frame shows a code that has moved. Three deliberate things:

- **The camera is unbound on dispose** — leaving the screen does not end the
  lifecycle the use cases are bound to.
- **`ScanGate` reports a code once**, or the same group is added repeatedly. Split
  out of `QrCodeAnalyzer` so it can be tested on the JVM.
- **The permission is requested by `CameraGate`**; refused twice it can only be
  restored from app settings (`ui/AppSettings.kt`).

The camera is `required="false"`, and `FEATURE_CAMERA_ANY` includes front-only
devices.

**`camera-video` is excluded from `camera-view`** — 1.3 MB, and a constraint:
`CameraController` is the one class in `camera-view` that needs it, so **do not
use `CameraController` or `LifecycleCameraController`**. Use cases are bound by
hand through `ProcessCameraProvider`; only `PreviewView` is taken from
`camera-view`, and it touches `CameraController` solely through `setController`,
never called. Reaching for one compiles and then fails at runtime with
`NoClassDefFoundError`. `QrViewfinderTest` pins both halves on a device.

That exclusion also pinned two permissions: `camera-video` pulled in
`media3-common`, the only thing declaring `ACCESS_NETWORK_STATE` that
`Connectivity` needs. That and `INTERNET` (from OkHttp) are now declared in the
app's own manifest.

## Receipts and attachments

**Neither is built, and both are deliberate.** The instance can do both; what it
does not do is give a client a way in. Checked against upstream `main`.

**Receipt scanning** is server-side OpenAI, opted into with
`ENABLE_RECEIPT_EXTRACT` and `OPENAI_API_KEY`. There is **no endpoint** —
extraction is a Next.js server action addressed by a build-specific id stripped
from production chunks. **The flags are not exposed**: `getRuntimeFeatureFlags()`
is `'use server'`. **The image must already be on the instance's own S3**, and
`isAllowedUploadUrl` rejects any other host. Reading receipts on the device
instead is the divergence this app avoids everywhere else, and an OCR engine costs
megabytes per ABI for a feature the server owns.

**Attachments** are one step less absolute. `documents` **is** in the expense
schema (`{id, url, width, height}`) and `:api` carries them both ways. The upload
is what is missing: `POST /api/s3-upload` is `next-s3-upload`'s own route, not a
Spliit contract, handing back temporary S3 credentials — so using it means an S3
client against an undeclared endpoint owned by a third-party library.
`ENABLE_EXPENSE_DOCUMENTS` sits behind the same `'use server'` flags.

**An edit still has to carry them.** `updateExpense` deletes every document whose
id the payload leaves out, so `ExpenseRepository.update` reads the current set back
**from the server** and sends it unchanged. Not from the cache, which stores none:
the payload is taken as whole truth, and a stale set would recreate a document
deleted elsewhere. An edit that cannot read the set back is not sent.

**Both stay absent until upstream exposes a contract.** Do not rebuild either by
scraping the RSC payload, guessing a server-action id, or reverse-engineering the
upload route.

## Network access is not guaranteed

`INTERNET` is granted at install on stock Android. **On GrapheneOS the user can
revoke it per app**, and two things measured there are counter-intuitive:

- `checkSelfPermission(INTERNET)` returns `PERMISSION_GRANTED` while `dumpsys
  package` reports `granted=false`.
- A failed request is no signal: DNS is blocked too, so it fails as
  `UnknownHostException`, identical to having no connection.

What works is opening a socket — `new Socket()` throws `SocketException: socket
failed: EPERM`. `AndroidNetworkAccess` probes loopback, nothing leaves the device,
and a *refused connection* is the success case, because the socket was created.

The two states need opposite advice ("check your connection" versus "turn network
access back on") and an app cannot ask for this permission back, so the server
screen blocks Continue and sends the user to Android settings. `spliitHttpClient`
takes an optional `HttpLogger`, wired to logcat in debug builds only, because
request URLs carry group ids.

### Cleartext is allowed, on purpose

`BaseUrl` accepts `http`, because a self-hosted instance on a LAN usually has no
certificate. From **Android 9 the platform blocks cleartext by default**, so
without saying otherwise the app would accept the address and then fail every
request as `SpliitError.Network` — reading as "check your connection", and working
on one phone but not the next.

`res/xml/network_security_config.xml` permits it, naming **system-only trust
anchors** (the API 28+ default) so cleartext is the one thing relaxed and a
user-installed certificate still cannot read traffic to a group. A third lint
warning is therefore deliberate: `InsecureBaseConfiguration`.

## Settlement

`core.settlement` is the port of upstream's `shares.ts` and `balances.ts`.
`expenseShares` is the one definition of what a participant owes; `settle`
reproduces `groups.balances.list` locally.

**It has to stay identical to upstream.** `SharesTest` and `BalancesTest` are ports
of the upstream suites with their expected values and random seeds. **Do not "fix"
an expectation to match a change here.** Change the algorithm only to track a
change upstream.

The invariant: shares are apportioned by largest remainder so they add to exactly
the expense amount, with the leftover minor unit rotated by an FNV-1a hash of the
expense id. Balances therefore sum to zero.

One deliberate divergence: exact `Long` arithmetic where upstream divides in
floating point. They differ only past 2^53.

**Every number a screen shows about a participant comes from here** —
`participantShare`, `participantBalanceChange`, `expenseShares` — never from
arithmetic in a ViewModel. A feed disagreeing with the balances screen would be
worse than one with no numbers.

**Reimbursements move a balance without being spending.** Out of "total spent",
out of a share, out of every figure on Totals, but counted in full towards a
balance.

## Testing

`test/` vs `androidTest/` is about **where code runs**: `src/test/` is the JVM
(fast, no emulator, framework calls are stubs that throw), `src/androidTest/` is a
device.

- Settlement, currency, superjson → `src/test/` in `:core` / `:api`
- tRPC against MockWebServer → `src/test/` in `:api`
- Repositories and the cache → `src/test/` under Robolectric against a **real
  in-memory Room database** (`testCache`). Real rather than faked: the mapping,
  query ordering and cached-then-fresh sequencing are what is worth testing. Both
  Room executors are the test dispatcher, so `advanceUntilIdle()` drives the
  database; `CacheProbeTest` pins that.
- DAOs against real SQLite, and the camera → `androidTest/`. `SpliitDatabaseTest`
  is there because `groups` is close to a reserved word and every query quotes it.
- Compose tests → `src/test/` with Robolectric.

Compose tests need `@Config(application = Application::class)` (booting
`ArdoiseApplication` would `startKoin` twice) and `@Config(qualifiers =
"w411dp-h891dp")` (the default window is too short, so controls below the fold
exist but are not displayed). Use `performScrollTo()` regardless.

Three limits worth knowing:

- **A text field inside a dialog window never lets Compose report itself idle.**
  Render the body composable, not the dialog.
- **`createAndroidComposeRule` drives the composition's effects from its own
  clock**, so work resumed by an outside executor — a CameraX bind — never
  advances and `waitUntil` times out. Use a real activity there.
- **The unit test heap is 2g**; Robolectric boots a framework per class and
  running out surfaces as every Compose test failing at once.
- **Every Turbine `test { }` passes `TURBINE_TIMEOUT`**: Turbine waits 3 seconds of
  *real* time while these run on a virtual clock.

Do **not** set `unitTests.returnDefaultValues = true` to silence an unmocked-stub
error: every framework call returns null/0 and tests pass while testing nothing.

## Conventions

- Compose: stateless composables, state hoisted to ViewModels, one immutable UI
  state class per screen exposed as `StateFlow`
- No business logic in composables — it belongs in `:core`
- Errors from `:api` are a sealed result type, never exceptions across a module
  boundary
- User-facing strings go in `strings.xml`, and **a new string goes into
  `values-fr` in the same commit**
- Comments explain what the code cannot: a rule, a constraint, a deliberate
  deviation. Not what the next line does

## Commits

**Conventional Commits**, and **short**: `type(scope): subject`, imperative, under
72 characters, no trailing full stop.

    feat(expense) fix(balances) refactor(api) build(camera)
    docs(readme) test(outbox) chore(deps) perf(feed)

Scope is the area, not the file — `expense`, `balances`, `group`, `server`,
`outbox`, `cache`, `icon`, `ci` — omitted when a change is not about one.

**A body is the exception.** Add one only when the *why* would otherwise be lost,
wrapped at 72. Never list changed files or restate the diff; `git show` does both.
Reasoning that outlives the commit belongs in this file or beside the code.

Commits carry a `Co-Authored-By` trailer and **nothing else** — in particular **no
`Claude-Session`**: the repository is public and history is not practically
rewritable once pushed.

`v*` tags drive releases: `git tag v1.2.3` builds, signs and publishes. The tag is
the version, so `versionCode` and `versionName` are never edited by hand.

## Backup

`allowBackup` is **off**, and `data_extraction_rules.xml` excludes cloud backup
again because the flag alone stops covering it from Android 12. A group id is the
only credential Spliit has, and DataStore holds every one the device knows.

Device-to-device transfer is deliberately left on: it reaches the phone replacing
this one without passing through a server.

## Settled decisions

Follow these rather than re-opening them.

**DI: Koin.** The graph is small, so runtime resolution costs little and no codegen
keeps builds fast. Two rules keep it safe and make a move to Hilt a one-file
change: **constructor injection only** (nothing outside `di/AppModule.kt` calls
`get()` or `inject()`), and **`AppModuleTest` must keep passing** — it runs Koin's
`verify()` on every module, turning a missing binding into a failing JVM test
rather than a crash on the screen that needed it. Koin lives only in `:app`.

**Persistence: DataStore for what cannot be refetched, Room for the rest.** The
rule is **asymmetric failure cost**, not "Room is a cache". DataStore holds the
base URL, group ids **per server**, the active participant per group, default
currency, language and theme. "Which one is you" has no field on the server at
all: do not add it to `GroupInput`, and note `updateGroup`'s own `participantId`
only attributes an activity-log entry. Room holds everything refetchable, in
`:app` under `data/local`, never `:core`.

**`fallbackToDestructiveMigration(dropAllTables = true)` is only safe while that
line holds.** Anything durable put in the cache database is silently destroyed by
the next schema change. The split costs one thing: the group list re-orders in
Kotlin, because `IN (:ids)` does not preserve a parameter list's order.

**Receipts and attachments: the instance's, or nobody's.** Spliit does both
server-side and per-instance, so the app delegates or does without. Nothing
on-device as a fallback — it would be maintained and shipped for a feature the
server owns, and would still disagree with the server wherever both ran.
