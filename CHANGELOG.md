# Changelog

Every release of `com.mfxhub:mfx-sdk`, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Until 1.0.0, a minor version may change the public
surface; each such change is listed under **Changed** with what to do. Each entry names what it derives
from in the Python SDK.

## Unreleased

## 0.1.0

### Fixed

- An answer carrying an enum word this version was not generated with no longer fails the call.
  OpenAPI Generator's `enumUnknownDefaultCase` gives every generated enum `UNKNOWN_DEFAULT_OPEN_API` (an enum of
  numbers `NUMBER_unknown_default_open_api`), and `fromValue`, which Jackson reads with, returns it for a word
  the enum does not list. The two enum templates in `tools/templates` make a nullable enum do the same rather than
  read the word as null. A request refuses the member before anything is sent, as a filter or in a body, with an
  `IllegalArgumentException` naming the enum. Shared suite 0.13.0 (5e57a41).

### Changed

- `DueDocument.graceEndsAt` reads `grace_ends_at` only, when the grace period ends or ended, for both agreement
  refusals. The contract renamed `terms_not_accepted`'s `grace_ended_at` to it and the platform sends
  only `grace_ends_at`, so the fallback to the old name is gone, as in Python. The shared suite is
  pinned at mfx-sdk-conformance 0.12.0 (d492672), whose Terms case sends `grace_ends_at`.
- The comment-limit `IllegalArgumentException` reads "comment is at most 31 characters; the terminal would cut a
  longer one (N given)". Doc comments say "the terminal" or "a MetaTrader account" where they named
  the platform's product, and "host" for where a terminal runs.

- The full and partial closes take a comment for the closing deal, as Python's do since 51860d8 and
  .NET's since 6a18a55: `closePosition(id, ticket, deviationPoints, comment, key)` and
  `closePositionPartial(id, ticket, volume, deviationPoints, comment, key)` on `execution()` and `tradingAccounts()`,
  either nullable. Without a comment none is sent, and the deal takes the position's own. Over `COMMENT_MAX`
  code points throws the same `IllegalArgumentException` as `placeOrder`, before any request.
  Close-by takes none, because its body has no comment. `closePosition`'s doc no longer says the API refuses a
  comment. `spec/surface.json` is re-vendored from Python 51860d8, which adds those `comment` arguments.
- `execution().placeOrder` throws `IllegalArgumentException` for a comment over `Execution.COMMENT_MAX` (31)
  characters, counted in Unicode code points as the contract's `maxLength` counts them, before anything is sent.
  Until now it was sent for the platform to refuse. This matches Python e0b76c7 and TypeScript. What
  to do: keep comments to 31 characters, or catch the exception where a comment comes from a person.

### Added

- `docs/sdkref.json`: a type that represents one contract object names it in `schema`, verbatim as its key of
  `components.schemas`, so the docs site links it to that object. 27 of the 195 types carry
  one, each derived and none written by hand (`SdkRefSchemas`): an event's type takes the schema the contract's
  `x-event-payloads` gives its `KIND` (`CandleClosed` is `MdCandleClosed`), or the event type
  `tools/sdkref/schemas.json` names for the five readers whose kind is their client's constant (`Book` is
  `BookMessage`); a record or enum takes the one generated model whose properties or values are exactly its own
  (`Tickets` is `CommandTickets`, `PaymentAction` is `BillingAction`). Clients, builders, errors, interfaces,
  generic pages and helpers carry none. The generated models keep the contract's names, and are not in the
  reference.
- The shared suite is pinned at mfx-sdk-conformance 0.11.0 (70ce249), whose checker refuses a `schema` the
  contract lacks and a parameter default elided with `...`. CI passes it `--spec spec/openapi.yaml`, so the
  names are checked against this repository's vendored contract. No case changed.
- `docs/sdkref.json`, the SDK reference in the shared format `mfx-sdkref/1`, generated from the
  public API and its Javadoc by the doclet in `tools/sdkref` (`./mvnw test -Dtest=SdkRefTest -Dsdkref.write=true`).
  Pages and intros are `tools/sdkref/pages.json`; each method's contract operation is `tools/sdkref/operations.json`,
  checked by calling every resource method; an error's code is the SDK's own code table. A method's overloads are
  one member with the fullest signature, the others listed in its doc; a nested type's anchor is `Outer_Inner`.
  CI fails when the committed file differs from a fresh generation, and runs the suite's checker on it.
- The shared suite is pinned at mfx-sdk-conformance 0.10.1 (dd095c5), which adds `sdkref/` (schema, page list and
  checker); `tools/update-suite.ps1` vendors it. No case changed.

- `spec/surface.json` re-vendored from Python c26b296 (mfx-sdk 0.19.0), which names `ServiceAgreementNotAccepted`,
  `TermsNotAccepted` and `tenant.service_agreement`. The surface map's `ahead` section is gone, and the surface
  and error-table checks read a Python error named without `Error` as its `...Exception`.
- The shared suite is pinned at mfx-sdk-conformance 0.9.0 (7d27547, the release commit; untagged, since a tag
  publishes), vendored from the internal suite: its 8 plan-agreement cases pass through the runner's new
  `tenant.service_agreement` and `agreement.due_warnings` operations, and `errors.service_agreement_not_accepted`
  and `errors.terms_not_accepted` expect the typed errors: 160 cases, none not yet.
- Plan agreements, from the contract at
  9af2676, the commit Python pins (cec56f6), while Python's `ServiceAgreementNotAccepted`,
  `TermsNotAccepted` and `tenant.service_agreement()` are written with the same meaning:
  - `ServiceAgreementNotAcceptedException` (a `ConflictException`) for 409 `service_agreement_not_accepted`, with
    `planChange()` (the generated `PlanChangeSummary`; null when no change), `documents()` and `acceptUrl()`.
  - `TermsNotAcceptedException` (a `ConflictException`) for 409 `terms_not_accepted`, with `documents()`. It
    arrived as the bare `ConflictException` before; a `catch (ConflictException e)` still catches it.
  - `DueDocument`: each document version due, with its key, number, plan and the end of its grace period.
  - `client.tenant().serviceAgreement()` over `GET /tenant/service-agreement`, the generated `TenantServiceAgreement`.
  - One warning per client, on the `System.Logger` named `MfxClient.LOGGER_NAME` (`com.mfxhub.sdk`), the first
    time an answer carries `MFX-Service-Agreement: due`; never again on that client.
  - `spec/openapi.yaml` re-vendored at the contract's 9af2676. The two codes are mapped ahead of Python's
    `surface.json`, held in the surface map's `ahead` until the surface is re-vendored with them.
- The shared suite is pinned at mfx-sdk-conformance 0.8.0 (3148983, the commit Python pins), vendored from the
  internal suite until its public release: its 23 Copy Trading cases pass through the runner's copy operations,
  and the error cases follow the current contract.
- Enriched 8/8, from Python's `errors.py` and `billing.py` (!97, 40bea91):
  - `PaymentRequiredException` for any 402, and beneath it `BillingPaymentRequiredException` for
    `billing_payment_required`, with `tenantId`, `billingProfileId`, `stage`, `billingCause` (never
    `getCause()`, which is the Java exception's own), `since`, `suspendsAt`, `payBy` and `action`, a
    `PaymentAction` (the contract's `BillingAction`, renamed beside the generated model of that name) with its
    exact `amountUsd`. An execution call refused for billing completes as a refused `ExecutionResult`.
  - `client.billing()`: `priceList`, `summary`, `profile`, `forecast`, `prices`, `invoice`, `invoicesPage`
    and `invoices` (with `ListInvoices`).
  - A test refuses any exception member named after one of `Throwable`'s.
  - The surface map holds nothing as pending: every entry of Python's `surface.json` maps.
- Enriched 7/8, from Python's `copy_trading.py` and the copy readers of `events.py` (b3bad52):
  - `client.copy()` over every `/copy` route. Leaders: `createLeader` (`CreateLeader`), `getLeader`,
    `leaders`/`allLeaders`, `updateLeader` (`UpdateLeader`), `pauseLeader`, `resumeLeader`, `retireLeader`,
    `sendOpenSignal` (`OpenSignal.volume(...)` or `OpenSignal.units(...)`: exactly one by construction) and
    `sendCloseSignal`. Followers: `createFollower` and `updateFollower` (`FollowerMandate`), `previewFollower`
    (a read: no key), `getFollower`, `followers`/`allFollowers`, `pauseFollower`, `resumeFollower`,
    `resyncFollower`, `unfollow`; their stop-outs: `stopouts`/`allStopouts`, `resetStopouts`. Groups:
    `createGroup`, `getGroup`, `groups`/`allGroups`, `updateGroup` (`UpdateGroup`, with named clears),
    `addGroupFollowers`, `removeGroupFollowers`, `pauseGroup`, `resumeGroup`, `closeGroup`, `rolloverGroup`
    (`RolloverGroup`; a dry run takes no key, an applied run must). Requested closes: `closes`/`allCloses`,
    `getClose`. The tenant's reads: `log`/`allLog`, `signals`/`allSignals`, `tenantStopouts`/`allTenantStopouts`.
    Every page is a `CopyPage` with the contract's `stats` row; every filter is a builder (`ListCopyLeaders` and
    the rest), several values comma-joined and a window that ends before it starts refused.
  - Every body is checked against the contract's request model before sending: a name the contract does not
    have, a word it cannot hold, a double anywhere, or a size below zero is refused at the call.
  - `Copy.REFUSAL_REASONS`, and the copy events from `Events.eventOf`: `CopyCopied`, `CopyRefused` (with its
    `RiskRefusal`), `CopyStopoutEvent`, `CopyDrift`, `CopyStateChanged` and `CopyClosedEvent`, with their parts
    `CopyDeal`, `CopyAs`, `CopyClosed` and `CopyNotClosed`.
  - The suite runner has the copy operations of mfx-sdk-conformance 0.8.0, ready for the pin to move; the
    suite stays at 0.7.1 in this pull request.
- Instants in a query or a body are written as Python's `isoformat()` writes them: seconds always, six digits
  of fraction when there is one, the offset as `+00:00`.
- Enriched 6/8, from Python's `webhooks.py` and `expert_advisors.py` (b3bad52):
  - `client.webhooks()`: `create`, `list`, `get`, `update` (with `UpdateWebhook`: address, types,
    description, paused), `delete`, `rotateSecret`, `test`, `failures` (by state, following the cursor)
    and `replay` (`ReplayWebhook.after` or `ReplayWebhook.failureId`: exactly one by construction);
    `Webhooks.verify` checks a delivery's `MFX-Signature` against one or several secrets and the time,
    failing with `WebhookSignatureException` (`malformed`, `stale` or `mismatch`).
  - `client.expertAdvisors()`: `upload` (each file's source sent as base64 of its UTF-8), `get`, `page`,
    `list`, `report`, `delete` and `waitForVerdict`; `ExpertAdvisors.telemetryOf` reads an `ea:` channel's
    `EaTelemetry` (its `EaRecord`s) and `EaTelemetryLost`.
- Enriched 5/8, from Python's `risk.py` and `events.py` (b3bad52 with `archive_challenge`, !96):
  - `client.risk()` over every `/v1/risk` route: policies (`createPolicy`, `addPolicyVersion`,
    `archivePolicy`, `activate`, `policy`, `policyVersion`, `policies`, `policyVersions`), accounts
    (`accountsPage`, `accounts`, `account`, `equity`, `closeScope`, `deactivate`, `clear`, `coverage`),
    the planner (`preview`, `check`), exit plans (`createPlan`, `plan`, `plansPage`, `plans`, `amendPlan`,
    `pausePlan`, `resumePlan`, `endPlan`), challenges (`createChallenge`, `addChallengeVersion`,
    `archiveChallenge`, `challenge`, `challenges`, `enrol`, `challengeAccountsPage`, `challengeAccounts`,
    `challengeAccount`) and the activity (`activityPage`, `activity`). Lists filter with `ListRiskPolicies`,
    `ListRiskAccounts`, `ListRiskPlans`, `ListChallengeAccounts` and `ListRiskActivity`. Rules, orders and
    steps are maps in the API's names; a `BigDecimal` in them goes as exact text and a double is refused.
  - The risk events from `Events.eventOf`: `RiskRuleEvent`, `RiskCoverageChanged`, `RiskProgrammeResult`
    and `RiskPeriodClosed`.
  - The shared suite's `risk` area passes (6 cases): `not-yet.json` is empty.
- Enriched 4/8, from Python's `md.py` and `events.py` (b3bad52):
  - `client.md()`: `subscribe` (with `Timeframe`s for candles), `subscriptions`, `allSubscriptions`,
    `subscribeAccount`, `accountSubscriptions`, `allAccountSubscriptions` (a cTrader account), `unsubscribe`,
    `candles`, `accountCandles`, `ticks`, and `allTicks`, which reads a window a call at a time and yields each
    tick once across the seams; `MarketData.bookOf` (`Book`) and `MarketData.subscriptionEvent`
    (`SubscriptionDegraded`, `SubscriptionRecovered`).
  - The typed stream events: `Events.eventOf` reads a message as `OrderPlaced`, `OrderModified`, `OrderClosed`,
    `PositionOpened`, `PositionModified`, `PositionClosed`, `TerminalStateChanged` or `CandleClosed` (by its
    channel), each a record of the sealed `Event`, decimals exact; any other type is null.
  - The shared suite's `md` area passes (4 cases).
- Enriched 3/8, from Python's `trading_accounts.py` and `live_account.py` (b3bad52):
  - `client.tradingAccounts()`: `get`, `page` and `list` (with `ListTradingAccounts`: venues, environment,
    states), `funds`, `positions`, `pendingOrders`, `deals` (a window, one position's when given),
    `symbols`; every execution command on `/accounts/{id}`, completing with an `ExecutionResult`; and the
    cTrader grant flow: `startCtraderGrant`, `ctraderGrant`, `connectCtraderAccounts`, `revokeCtraderGrant`.
  - `LiveAccount` and `AccountView`: an `account:` channel's snapshot with its marks applied, by the
    stream's ordering rules; a mark that cannot apply leaves the view `behind()` until the next snapshot.
- Enriched 2/8, from Python's `execution.py`, `results.py` and `accounts.py` (b3bad52):
  - `client.execution()`: `placeOrder` (with `PlaceOrder`), `cancelOrder`, `modifyPosition`
    (`ModifyPosition`), `modifyOrder` (`ModifyOrder`, whose expiry is kept, cleared with
    `goodTillCancelled()`, or set), `closePosition`, `closePositionBy`, `closePositionPartial`,
    `setTrailingStop`, `clearTrailingStop`, `getCommand` and `waitFor`. Every one completes with an
    `ExecutionResult` and never exceptionally: a refusal, a rate limit, a maintenance window, a dropped
    connection and a timeout are results, each with `retryable` as the platform's rules say, and a
    final duplicate is read once for the original result. Every decimal is a `BigDecimal`, written plainly;
    no execution method takes a double.
  - `ExecutionResult`, `Reason`, `CommandState`, `Tickets` and `VenueDeal`.
  - `client.accounts()`: `account`, `positions`, `pendingOrders`, `deals` (a window, paged) and `spread`.
  - The shared suite's `decimals`, `idempotency` and `execution` areas pass (13 cases); the runner adds
    `terminals.create`, `execution.place_order`, `catalog.quote` and the `refuses` observation.
- Enriched 1/8, from Python's `terminals.py`, `catalog.py`, `farms.py`, `usage.py`,
  `maintenance.py` and `expert_advisors.Tenant` (b3bad52):
  - `client.terminals()`: `create` (with `CreateTerminal`), `submitCredentials` (the password a
    `Secret`, printed `***`), `restart`, `attachExpertAdvisor` and `detachExpertAdvisor` (with
    `Relaunch`: a restart queues unless told not to, an expert advisor does not), `confirmExpertAdvisor`,
    `replaceInstruments`, `journal` (1 to 200 lines, checked here), `getOperation`, `waitForOperation`,
    `waitUntilActive` (a QUEUED terminal read again at its expected start), `bulkRestart` (with
    `BulkRestart`, checked here), `getBulkAction`, `cancelBulkAction`, `drain` (with `DrainTerminal`),
    `resume` and `retire`; `Terminals.isUsable`, `ON_ITS_WAY`, `OPERATION_FINAL`, `OFFLINE_IS_TEMPORARY`.
  - `client.catalog()`: `brokers`, `allBrokers`, `servers`, `catalogues`, `symbols`, `symbol` and `quote`,
    the server and account type named with `WhichServer`; a symbol is one percent-encoded path segment.
  - `client.farms()`, `client.usage()`, `client.tenant().contacts()`, and `client.maintenance()` (`page`,
    `list` with `ListMaintenance`, `terminalsPage`, `terminals`) with `Maintenance.windowEvent`.
  - Every write takes the caller's idempotency key: null or blank is refused before anything is sent.
- The contract (`spec/openapi.yaml`) and the reference surface (`spec/surface.json`) re-vendored from
  Python b3bad52 (the contract at ff54f71); the surface check covers every phase, with what has not landed held
  as pending in `tools/surface-check/map.json`.
