# MFX Hub Java SDK

The typed Java client for the [MFX Hub](https://mfxhub.com) API: hosted trading terminals for your
own broker accounts, with market data, execution, account state and a live event stream behind one
signed REST and WebSocket API.

Maven:

```xml
<dependency>
  <groupId>com.mfxhub</groupId>
  <artifactId>mfx-sdk</artifactId>
  <version><!-- the latest release --></version>
</dependency>
```

Gradle:

```kotlin
implementation("com.mfxhub:mfx-sdk:<the latest release>")
```

Java 21 or later. HTTP, the WebSocket and Ed25519 are the JDK's own; the one runtime dependency is
Jackson. Every release passes the same shared test suite as the Python and TypeScript SDKs.

## Your first quote

You need an API key (its id, and the private key shown once when it was issued), your broker's
name, and your server's name exactly as your trading platform's login window shows it. A quote is
read from one of your terminals on that server, so ask for one first, in the
[portal](https://portal.mfxhub.com) or with `client.terminals()`.

```java
import com.mfxhub.sdk.MfxClient;
import com.mfxhub.sdk.SigningKey;
import com.mfxhub.sdk.WhichServer;
import com.mfxhub.sdk.models.SymbolQuote;
import com.mfxhub.sdk.models.TenantBroker;
import com.mfxhub.sdk.models.TenantBrokerServer;
import java.net.URI;

public final class FirstQuote {
    public static void main(String[] args) {
        URI api = URI.create("https://api.dev.mfxhub.com/v1"); // https://api.mfxhub.com/v1 for production
        SigningKey key = SigningKey.parse(System.getenv("MFX_PRIVATE_KEY")); // PEM, or base64
        try (MfxClient client = MfxClient.builder(api, System.getenv("MFX_KEY_ID"), key).build()) {
            // Every request below is signed with your key; the key itself is never sent, logged or printed.
            // 1. The ids for the names you know: your broker, and your server.
            String brokerName = System.getenv("MFX_BROKER");
            TenantBroker broker = null;
            for (TenantBroker b : client.catalog().allBrokers()) { // follows the cursor, page by page
                if (b.getCode().equalsIgnoreCase(brokerName) || b.getDisplayName().equalsIgnoreCase(brokerName)) {
                    broker = b;
                    break;
                }
            }
            if (broker == null) {
                throw new IllegalStateException("no broker named " + brokerName);
            }
            TenantBrokerServer server = null;
            for (TenantBrokerServer s : client.catalog().servers(broker.getId()).join()) {
                if (s.getServerName().equals(System.getenv("MFX_SERVER"))) {
                    server = s;
                }
            }
            if (server == null) {
                throw new IllegalStateException("no server named " + System.getenv("MFX_SERVER"));
            }

            // 2. A quote, always with its age. Prices are exact decimal text.
            SymbolQuote quote = client.catalog()
                    .quote(broker.getId(), "EURUSD", WhichServer.builder().serverId(server.getId()).build())
                    .join();
            System.out.println(quote.getBid() + " / " + quote.getAsk() + ", " + quote.getAgeS()
                    + "s old, market " + quote.getState());
        }
    }
}
```

Run it with `MFX_KEY_ID`, `MFX_PRIVATE_KEY`, `MFX_BROKER` (for example `Pepperstone`) and
`MFX_SERVER` (for example `Pepperstone-Demo`) set. Use a **demo** account while you try the SDK: an
order is real on whatever account the terminal is logged in to. The
[knowledge base](https://github.com/mfx-platform/mfx-kb) walks through a first order step by step.

## Reference

The [SDK documentation](https://docs.mfxhub.com/sdk) has guides and the full reference for every
client: terminals, execution, accounts and the catalogue, market data, the stream, Risk Management,
Copy Trading, billing and errors. The API itself is described at [docs.mfxhub.com](https://docs.mfxhub.com).

## What to know

- **Calls are asynchronous.** Each returns a `CompletableFuture`; `join()` waits for it, and a
  failure arrives as the typed exception below, wrapped in a `CompletionException`.
- **Every write takes an idempotency key, and none defaults it.** Retrying with the same key
  returns the original answer; a fresh key asks for a second terminal or a second order.
- **Execution methods never fail** for what the platform or the network does. A transport fault, a
  timeout, a rate limit and a broker rejection all complete as an `ExecutionResult` with a granular
  reason, the broker's own comment and whether a retry may help. A result in state `UNKNOWN` may
  already be a position: never re-send it under a new key; the platform reconciles it.
- **Prices, volumes and money are exact.** You pass `BigDecimal`; the SDK sends and reads decimal
  text, so `0.07 * 3` is never `0.21000000000000002`.
- **Reads fail with typed exceptions**, mapped from the API's `error.code`: `NotFoundException`,
  `RateLimitException` with the time to wait, `UpstreamException` (with
  `DependencyUnavailableException` and `EdgeException` beneath it), `MaintenanceException` with the
  window's end, and the rest, all `MfxApiException`s.
- **Risk Management refuses an entry with its reason.** An order a rule refuses fails with
  `RiskRefusedException`, which names the rule, its limit and the figures. Closes, partial closes
  and cancels are never refused.
- **Credentials cannot be logged by accident.** `SigningKey` and `Secret` render as `***` in every
  `toString`, are not `Serializable`, and Jackson never writes them.
- **A word from a newer API reads, it does not fail.** When an answer carries an enum word this
  version was not built with (a close's cause, a terminal's state), the field reads as that enum's
  `UNKNOWN_DEFAULT_OPEN_API`; a newer SDK names it. A request refuses that member before anything is
  sent, with an `IllegalArgumentException`.
- **Lists follow the cursor.** There are no totals and no page numbers; a method that returns an
  `Iterable` walks every page, one request at a time, and one that returns a `Page` returns one.
- **What your organisation must accept.** While a plan change, a Service Agreement or the Terms wait
  to be accepted, additions (a terminal, an expert advisor, an API key, higher limits) fail with
  `ServiceAgreementNotAcceptedException` or `TermsNotAcceptedException`; nothing running is refused.
  `client.tenant().serviceAgreement()` says what is due, and the first answer that says
  `MFX-Service-Agreement: due` logs one warning on the `com.mfxhub.sdk` logger (`System.Logger`).
- **The stream** (`client.stream()`) carries market data, candles, the order book, account state,
  results and events on one WebSocket, resumed per channel after a reconnect.

## How this package is built

The models in `com.mfxhub.sdk.models` are generated from the published MFX Hub API description in
`spec/openapi.yaml` at every build, by `tools/GenerateModels.java`, so nothing generated is
committed to drift. The hand-written clients around them (`MfxClient`, `Terminals`, `Execution`,
`Catalog` and the rest) are what you use.

```bash
./mvnw verify                               # build (Error Prone, warnings are errors) and unit tests
./mvnw -P release -Dgpg.skip package        # also the sources and Javadoc jars, unsigned
```

[CONTRIBUTING.md](CONTRIBUTING.md) has the rest.

## Support and security

- Documentation: [docs.mfxhub.com/sdk](https://docs.mfxhub.com/sdk).
- Questions and bugs: open an issue, or write to support@mfxhub.com.
- Contributing: see [CONTRIBUTING.md](CONTRIBUTING.md); every release is in [CHANGELOG.md](CHANGELOG.md).
- Security reports: see [SECURITY.md](SECURITY.md). Please do not open a public issue for one.

## Licence and trademarks

Licensed under the [Apache License 2.0](LICENSE).

MFX Hub is an independent service and is not affiliated with, endorsed by or sponsored by
MetaQuotes Ltd. MetaTrader is a registered trademark of MetaQuotes Ltd., used here only to say which
trading platform MFX Hub's terminals are compatible with.
