# Publishing setup: Confluence Publisher

How to wire AsciiDoc → Confluence publishing. The Maven plugin is the reference implementation;
a Docker image and a community Gradle plugin also exist.

> **Versions move.** At the time of writing the Maven artifact line is around `0.31.0`. Always
> confirm the current version on Maven Central
> (`org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-maven-plugin`) or the
> GitHub releases page rather than hard-coding an old one.

## Source tree = page tree

The folder layout under the root **is** the published page hierarchy. Each `.adoc` is a page; a
sibling folder named after the file holds its children and resources; `_`-prefixed files are includes
(not pages).

```
<asciidocRootFolder>
+- top-level-page.adoc
+- top-level-page/
   +- sub-page-one.adoc
   +- sub-page-two.adoc
   +- sub-page-two/
   +- images/            # resources, resolved relative to the referencing .adoc
```

UTF-8 is assumed unless `sourceEncoding` says otherwise.

## Maven plugin

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.sahli.asciidoc.confluence.publisher</groupId>
      <artifactId>asciidoc-confluence-publisher-maven-plugin</artifactId>
      <version><!-- current version from Maven Central --></version>
      <configuration>
        <asciidocRootFolder>etc/docs</asciidocRootFolder>
        <sourceEncoding>UTF-8</sourceEncoding>           <!-- default -->
        <rootConfluenceUrl>https://confluence.example.com</rootConfluenceUrl>
        <skipSslVerification>false</skipSslVerification>
        <maxRequestsPerSecond>10</maxRequestsPerSecond>
        <spaceKey>SPACE</spaceKey>
        <ancestorId>327706</ancestorId>                  <!-- parent page id -->
        <username>${confluence.user}</username>          <!-- or via serverId -->
        <password>${confluence.password}</password>
        <!-- <serverId>myServerId</serverId> -->         <!-- read creds from settings.xml -->
        <!-- <restApiVersion>v1</restApiVersion> -->      <!-- needed for some local installs -->
      </configuration>
    </plugin>
  </plugins>
</build>
```

Run: `mvn org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-maven-plugin:publish`
(or bind the `publish` goal to a phase).

### Key configuration

| Property | Meaning |
|----------|---------|
| `asciidocRootFolder` | root of the `.adoc` source tree (defines the page tree) |
| `rootConfluenceUrl` | base Confluence URL |
| `spaceKey` | target space key |
| `ancestorId` | id of the page the docs are published under |
| `username` / `password` | credentials; or use `serverId` to read from `settings.xml` |
| `serverId` | `<server>` id in `settings.xml`; `username`/`password` override it when both set |
| `sourceEncoding` | source charset (default UTF-8) |
| `skipSslVerification` | disable TLS verification (self-signed servers) |
| `maxRequestsPerSecond` | throttle to avoid overloading Confluence |
| `restApiVersion` | force `v1` for local/older instances |
| `versionMessage` | optional version comment on updated pages |
| `notifyWatchers` | whether watchers get change notifications |

## Important runtime behaviors

- **Orphan deletion:** publishing **removes every page under `ancestorId` that is not part of the
  published docs.** Publish to a dedicated space/subtree; don't mix in manually maintained pages.
- **Incremental:** only content changed since the last publish is re-published (content hashing).
- **Attachments:** referenced images / non-`.adoc` files are uploaded as page attachments; remote
  image URLs are linked, not downloaded.
- **Tested against** Confluence Server (historically 6.0.5+) and Confluence Cloud. Local installs may
  require `restApiVersion=v1`.
- **Title uniqueness:** Confluence requires unique page titles per space. Use the plugin's
  prefix/suffix support when publishing multiple doc sets into one space.

## Docker image

A prebuilt image runs the same conversion/publish without a Maven project — pass the same settings
as environment variables / arguments and mount the docs folder. Use it for CI runners without a JVM
build set up. Custom AsciidoctorJ extensions go in `/opt/extensions` inside the container and load
automatically.

## Gradle (community plugin)

A separate community Gradle plugin exists (not from the same org; property names differ). It exposes
a single `publishToConfluence` task:

```groovy
confluencePublisher {
    asciiDocRootFolder = tasks.asciidoctor.sourceDir
    outputDir          = "${buildDir}/docs/confluence"
    rootConfluenceUrl  = 'https://myconfluence.url.com'
    spaceKey           = 'MySpace'
    ancestorId         = '1234567'
    username           = 'MyConfluenceUser'
    password           = 'MySecretPassword'
    notifyWatchers     = false
}
```

Note the camelCase differences vs the Maven plugin (`asciiDocRootFolder`). Treat it as a separate
project with its own version/feature lag; verify behavior against its own docs.

## CI integration

- Store `username`/`password` (or a Confluence API token used as the password) as CI secrets, inject
  as Maven properties or env vars — never commit them.
- Typical pipeline: build → `publish` goal on merge to the docs branch.
- Use `maxRequestsPerSecond` to stay within Confluence rate limits on large doc sets.
- For air-gapped/self-signed servers, `skipSslVerification=true` and possibly `restApiVersion=v1`.

## Extensions

Custom AsciidoctorJ extensions (written in Java, built to a jar) are supported: add the jar as a
plugin `<dependency>` for Maven, or drop it into `/opt/extensions` for the Docker image. Use these to
support constructs the Publisher doesn't handle out of the box.
