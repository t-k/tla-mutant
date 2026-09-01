import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

plugins {
    application
    java
    `java-test-fixtures`
    id("com.diffplug.spotless") version "8.10.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("info.picocli:picocli:4.7.7")

    testImplementation(platform("org.junit:junit-bom:5.13.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val integrationTestSourceSet =
    sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.testFixtures.get().output
        runtimeClasspath += output + compileClasspath
    }

val sandboxTestSourceSet =
    sourceSets.create("sandboxTest") {
        java.srcDir("src/sandboxTest/java")
        resources.srcDir("src/sandboxTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.testFixtures.get().output
        runtimeClasspath += output + compileClasspath
    }

configurations[integrationTestSourceSet.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get(),
)
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get(),
)
configurations[sandboxTestSourceSet.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get(),
)
configurations[sandboxTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get(),
)

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against real SANY and TLC."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
}

val sandboxTest by tasks.registering(Test::class) {
    description = "Runs rootless Podman containment tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sandboxTestSourceSet.output.classesDirs
    classpath = sandboxTestSourceSet.runtimeClasspath
    shouldRunAfter(integrationTest)
}

tasks.check {
    dependsOn(integrationTest, "verifyFrozenApplication")
}

application {
    mainClass = "dev.tlamutant.cli.Main"
}

data class TlaToolsLock(
    val version: String,
    val vendorPath: String,
    val upstreamUrl: String,
    val upstreamRevision: String,
    val sizeBytes: Long,
    val sha256: String,
)

val lockFile = layout.projectDirectory.file("config/tla-tools.lock.json")
val toolsJar = layout.projectDirectory.file(".tools/tla2tools-1.8.0.jar")
val tlaToolsLock =
    providers
        .fileContents(lockFile)
        .asText
        .map {
            groovy.json.JsonSlurper().parseText(it) as Map<*, *>
        }.map {
            TlaToolsLock(
                version = it["version"] as String,
                vendorPath = it["vendorPath"] as String,
                upstreamUrl = it["upstreamUrl"] as String,
                upstreamRevision = it["upstreamRevision"] as String,
                sizeBytes = (it["sizeBytes"] as Number).toLong(),
                sha256 = it["sha256"] as String,
            )
        }
val vendoredToolsJar = layout.projectDirectory.file(tlaToolsLock.map { it.vendorPath })

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

fun verifyTlaToolsArtifact(
    path: Path,
    lock: TlaToolsLock,
    label: String,
) {
    val size = Files.size(path)
    require(size == lock.sizeBytes) { "$label size mismatch: $size" }
    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).hex()
    require(digest == lock.sha256) { "$label SHA-256 mismatch" }
}

fun resolveVendoredArtifact(
    projectRoot: Path,
    lock: TlaToolsLock,
): Path {
    val configured = Path.of(lock.vendorPath)
    require(!configured.isAbsolute) { "Vendored TLA+ Tools path must be relative" }
    val source = projectRoot.resolve(configured).normalize()
    require(source.startsWith(projectRoot)) { "Vendored TLA+ Tools path escapes the project" }
    var current = projectRoot
    projectRoot.relativize(source).forEach { component ->
        current = current.resolve(component)
        require(!Files.isSymbolicLink(current)) { "Vendored TLA+ Tools path must not contain symbolic links" }
    }
    require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
        "Vendored TLA+ Tools artifact must be a regular file"
    }
    return source
}

val provisionTlaTools by tasks.registering {
    inputs.file(lockFile)
    inputs.file(vendoredToolsJar)
    outputs.file(toolsJar)
    doLast {
        val lock = tlaToolsLock.get()
        val projectRoot =
            layout.projectDirectory.asFile
                .toPath()
                .toAbsolutePath()
                .normalize()
        val source = resolveVendoredArtifact(projectRoot, lock)
        verifyTlaToolsArtifact(source, lock, "Vendored TLA+ Tools")
        val destination = toolsJar.asFile.toPath()
        Files.createDirectories(destination.parent)
        val temporary = Files.createTempFile(destination.parent, ".tla2tools-", ".part")
        try {
            runCatching {
                Files.setPosixFilePermissions(
                    temporary,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
            Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
                Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            verifyTlaToolsArtifact(temporary, lock, "Temporary TLA+ Tools")
            Files.move(
                temporary,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

val fetchTlaTools by tasks.registering {
    description = "Compatibility alias for network-free vendored TLA+ Tools provisioning."
    dependsOn(provisionTlaTools)
}

val verifyTlaTools by tasks.registering {
    dependsOn(provisionTlaTools)
    inputs.file(toolsJar)
    inputs.file(lockFile)
    doLast {
        val lock = tlaToolsLock.get()
        val jar = toolsJar.asFile.toPath()
        verifyTlaToolsArtifact(jar, lock, "Cached TLA+ Tools")
    }
}

dependencies {
    compileOnly(files(toolsJar))
    testCompileOnly(files(toolsJar))
    testRuntimeOnly(files(toolsJar))
    add(integrationTestSourceSet.compileOnlyConfigurationName, files(toolsJar))
    add(integrationTestSourceSet.runtimeOnlyConfigurationName, files(toolsJar))
}

tasks.compileJava {
    dependsOn(verifyTlaTools)
}

tasks.jar {
    manifest.attributes["Main-Class"] = application.mainClass.get()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    filePermissions { unix("0644") }
    dirPermissions { unix("0755") }
}

val fatJar by tasks.registering(Jar::class) {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Main-Class"] = application.mainClass.get()
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}

val currentApplicationLock = layout.projectDirectory.file("config/current-application.json")
val verifyFrozenApplication by tasks.registering {
    description = "Verifies that the reproducible fat JAR matches the preregistered current application identity."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(fatJar)
    inputs.file(currentApplicationLock)
    inputs.file(fatJar.flatMap { it.archiveFile })
    doLast {
        val application = groovy.json.JsonSlurper().parse(currentApplicationLock.asFile) as Map<*, *>
        val expectedSize = (application["sizeBytes"] as Number).toLong()
        val expectedDigest = application["sha256"] as String
        val applicationJar =
            fatJar
                .get()
                .archiveFile
                .get()
                .asFile
                .toPath()
        val actualSize = Files.size(applicationJar)
        val actualDigest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(applicationJar)).hex()
        require(actualSize == expectedSize) { "Frozen application size mismatch: $actualSize" }
        require(actualDigest == expectedDigest) { "Frozen application SHA-256 mismatch: $actualDigest" }
    }
}

integrationTest {
    dependsOn(fatJar, verifyTlaTools)
    systemProperty(
        "tla.mutant.test.appJar",
        fatJar
            .flatMap { it.archiveFile }
            .get()
            .asFile.absolutePath,
    )
    systemProperty("tla.mutant.test.toolsJar", toolsJar.asFile.absolutePath)
}

sandboxTest {
    dependsOn(fatJar, verifyTlaTools)
    systemProperty(
        "tla.mutant.test.appJar",
        fatJar
            .flatMap { it.archiveFile }
            .get()
            .asFile.absolutePath,
    )
    systemProperty("tla.mutant.test.toolsJar", toolsJar.asFile.absolutePath)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat()
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
