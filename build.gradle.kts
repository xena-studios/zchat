import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "co.xenastudios"

/**
 * Version identity is semantic and driven by git tags (`vMAJOR.MINOR.PATCH`).
 *
 *  - Building the exact commit a `v*` tag points at  -> clean release, e.g. `2.0.0`.
 *  - Building any commit after the latest tag         -> nightly pre-release,
 *    e.g. `2.0.0-nightly.3+ab12cd34` (`<latest-tag>-nightly.<commits-since>+<sha>`).
 *  - No tags yet (or git unavailable)                 -> `0.0.0-nightly.<count>+<sha>`,
 *    so the build never fails on a fresh clone or a source export with no history.
 */
fun git(vararg args: String): String = runCatching {
    val process = ProcessBuilder(listOf("git") + args)
        .redirectErrorStream(false)
        .start()
    val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
    process.waitFor()
    if (process.exitValue() == 0) text else ""
}.getOrDefault("")

val shortSha: String = git("rev-parse", "--short=8", "HEAD").ifEmpty { "nogit" }
val fullSha: String = git("rev-parse", "HEAD").ifEmpty { "unknown" }
val buildTimestamp: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

/** Resolve a semantic version string from the git tag graph. */
val semanticVersion: String = run {
    // `git describe` reports "<tag>-<commits-since>-g<sha>" (commits-since is 0 on a tag).
    val described = git("describe", "--tags", "--long", "--match", "v*")
    val match = Regex("""^v?(.+)-(\d+)-g[0-9a-f]+$""").matchEntire(described)
    if (match != null) {
        val (base, commitsSince) = match.destructured
        if (commitsSince.toInt() == 0) base            // exactly on a tag -> clean release
        else "$base-nightly.$commitsSince+$shortSha"   // ahead of the tag -> nightly
    } else {
        // No matching tag yet: count commits so successive nightlies still sort.
        val count = git("rev-list", "--count", "HEAD").ifEmpty { "0" }
        "0.0.0-nightly.$count+$shortSha"
    }
}

version = semanticVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper API — provided by the server at runtime; bundles Adventure + MiniMessage and the
    // stable Brigadier command API (io.papermc.paper.command.brigadier), non-experimental as of
    // 1.20.6. A jar compiled against 1.20.6 runs unchanged on all newer Paper releases.
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    // Available in tests so classes that reference Bukkit types resolve; the unit tests
    // themselves only exercise server-free logic.
    testImplementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Inject the version + commit + build timestamp into paper-plugin.yml and build-info.properties.
tasks.processResources {
    val tokens = mapOf(
        "version" to project.version.toString(),
        "commit" to fullSha,
        "shortCommit" to shortSha,
        "buildTimestamp" to buildTimestamp,
    )
    inputs.properties(tokens)
    filesMatching(listOf("paper-plugin.yml", "build-info.properties")) {
        expand(tokens)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("zChat")
    // Nothing to relocate today (MiniMessage is provided by Paper) but shadow gives us a
    // single, predictable runnable jar and room to shade later without changing the build.
}

// `./gradlew build` should produce the shaded, runnable jar.
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Avoid emitting the thin jar so only the shaded jar lands in build/libs.
tasks.jar {
    enabled = false
}
