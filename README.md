# Tibu Collector Utils

Shared helpers for developers of [Tibu collectors](https://github.com/tibudget/tibudget-api).
Extend `AbstractCollectorPlugin` for HTTP requests, HTML and JSON parsing, downloads
and saved progress, or use the standalone parsing and request builders.

This is a Java library, not a collector or an HTTP client. Tibu supplies the
`InternetProvider`, authentication providers and DTO API at runtime.

## Requirements and installation

Use Java 21 and Maven. This version targets **Collector API 0.66**, where
`collect()` returns `boolean`. API 0.65 uses a different method signature and is
not compatible with this version of the base class.

Add JitPack and the dependencies to your collector's POM:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.tibudget</groupId>
        <artifactId>tibudget-api</artifactId>
        <version>0.66</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.github.tibudget</groupId>
        <artifactId>tibudget-collector-utils</artifactId>
        <version>UTILS_TAG</version>
    </dependency>
</dependencies>
```

Replace `UTILS_TAG` with the exact tag built on
[JitPack](https://jitpack.io/#tibudget/tibudget-collector-utils).
The changes on the development branch are not a published release. Older tags
may use the former `com.tibudget` dependencies and build configuration; consult
the README at the selected tag. Do not mix old and JitPack coordinates for the
same library or API in one dependency tree.

The API dependency is `provided`: your collector must declare it explicitly.
Gson, jsoup and the dependencies needed to package jsoup with Android Dex/R8 are
transitive dependencies. This project produces a regular JAR; the collector
project handles its own runtime packaging and Android compatibility checks.

## Start a collector

Implement `getDomain()` and `collect()`. Use a fresh collector instance for each
run, initialize it through the Collector API, then read its output and settings.

```java
import com.tibudget.api.exceptions.*;
import com.tibudget.utils.AbstractCollectorPlugin;
import com.tibudget.utils.CollectorUtils;
import org.jsoup.nodes.Document;

public final class ShopCollector extends AbstractCollectorPlugin {
    @Override
    public String getDomain() {
        return "https://shop.example";
    }

    @Override
    public boolean collect() throws CollectError, AccessDeny,
            TemporaryUnavailable, ConnectionFailure, ParameterError {
        Document history = get("/account/history");
        String owner = CollectorUtils.elementSelectionToText(history, ".account-owner");
        setConfigurationName(owner);
        // Authenticate and map the site's accounts and transactions here.
        // Put accounts in accounts and append completed DTOs to transactions.
        setProgress(100);
        return true;
    }
}
```

This shows the extension points; it is not a complete shop integration. Follow
the [Collector API and DTO rules](https://github.com/tibudget/tibudget-api)
for authentication, stable IDs, UUIDs, required fields and previous results.
See the [collector guide](docs/collectors.md) for providers, requests, downloads
and resumable work.

## Read values from HTML

```java
import com.tibudget.utils.AmountDto;
import com.tibudget.utils.CollectorUtils;
import org.jsoup.Jsoup;
import java.util.Locale;

var page = Jsoup.parse("<p class='quantity'>Quantité : 2 · 18,00 € / article</p>"
        + "<p class='total'>36,00 €</p>");
int quantity = CollectorUtils.elementSelectionToInteger(page, ".quantity"); // 2
AmountDto total = CollectorUtils.elementSelectionToAmount(page, ".total", Locale.FRANCE);
String optionalLabel = CollectorUtils.elementSelectionToText(page, ".label", "");
```

Each `elementTo…` helper also has an `elementSelectionTo…` variant using the first
CSS match. Without a default, a missing value raises `CollectError`. Integer and
amount helpers also raise it when parsing fails. Overloads with a default return
that default instead, which may be `null`. Invalid CSS selectors still raise
jsoup errors. See [parsing rules and examples](docs/helpers.md).

## Available helpers

| Helper | Purpose |
| --- | --- |
| `AbstractCollectorPlugin` | Provider calls, headers, rate limiting, JSON adapters, outputs and checkpoints |
| `CollectorUtils` | HTML text, integers, amounts, names, hashes and file helpers |
| `AmountParser`, `AmountDto` | Parse a numeric amount and optional currency |
| `PostData` | Build form or JSON string fields |
| `QueryParameters` | Build UTF-8 query parameters |
| `NextJSUtils` | Extract inline Next.js Flight JSON without running JavaScript |
| `CollectorPendingTransaction`, `CollectorTransactionPage`, `CollectorProgress` | Describe pending work and save completed references |
| `com.tibudget.utils.gson` | ISO date/time Gson adapters |

## Build, test and contribute

```sh
mvn verify
```

The build runs offline unit tests and creates binary, sources and Javadoc JARs
in `target/`. The first build needs network access to resolve Maven dependencies.
Open `target/reports/apidocs/index.html` for the generated API documentation.
Read [CONTRIBUTING.md](CONTRIBUTING.md) for checks and JitPack releases, and
[CHANGELOG.md](CHANGELOG.md) for changes affecting collector developers.

For recorded HTTP scenarios, see
[Tibu Collector Test Utils](https://github.com/tibudget/tibudget-collector-test-utils).
Select a version compatible with your Collector API; versions targeting API 0.65
cannot run API 0.66 collectors without adaptation.

Released under the [MIT License](LICENSE).


## JitPack build

JitPack uses Java 21 and the Maven Wrapper to run the tests and install the JAR,
sources, and Javadoc. The wrapper pins Maven 3.9.16 because the build plugins
require a newer Maven than the default JitPack image provides.

Run the same build locally with `./mvnw -B -ntp install`.
After publishing build changes, use a new tag containing them; an existing tag
still points to its original build configuration.
