import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
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
    dependsOn(integrationTest)
}

application {
    mainClass = "dev.tlamutant.cli.Main"
}

data class TlaToolsLock(
    val version: String,
    val url: String,
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
                url = it["url"] as String,
                sizeBytes = (it["sizeBytes"] as Number).toLong(),
                sha256 = it["sha256"] as String,
            )
        }

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

val fetchTlaTools by tasks.registering {
    inputs.file(lockFile)
    outputs.file(toolsJar)
    doLast {
        val lock = tlaToolsLock.get()
        val destination = toolsJar.asFile.toPath()
        Files.createDirectories(destination.parent)
        val temporary = Files.createTempFile(destination.parent, ".tla2tools-", ".part")
        try {
            val client =
                HttpClient
                    .newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
            var uri = URI.create(lock.url)
            var redirects = 0
            while (true) {
                require(uri.scheme == "https") { "TLA+ Tools URL must use HTTPS" }
                require(uri.host in setOf("github.com", "release-assets.githubusercontent.com")) {
                    "TLA+ Tools redirect origin is not allowed: ${uri.host}"
                }
                val response =
                    client.send(
                        HttpRequest.newBuilder(uri).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                if (response.statusCode() in 300..399) {
                    require(++redirects <= 5) { "Too many TLA+ Tools redirects" }
                    val location = response.headers().firstValue("location").orElseThrow()
                    uri = uri.resolve(location)
                    response.body().close()
                    continue
                }
                require(response.statusCode() == 200) { "TLA+ Tools download failed: ${response.statusCode()}" }
                response.body().use { input ->
                    Files.newOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= lock.sizeBytes) { "TLA+ Tools download exceeded expected size" }
                            output.write(buffer, 0, count)
                        }
                        require(total == lock.sizeBytes) { "TLA+ Tools size mismatch: $total" }
                    }
                }
                break
            }
            val digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(Files.readAllBytes(temporary))
                    .hex()
            require(digest == lock.sha256) { "TLA+ Tools SHA-256 mismatch" }
            runCatching {
                Files.setPosixFilePermissions(
                    temporary,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
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

val verifyTlaTools by tasks.registering {
    dependsOn(fetchTlaTools)
    inputs.file(toolsJar)
    inputs.file(lockFile)
    doLast {
        val lock = tlaToolsLock.get()
        val jar = toolsJar.asFile.toPath()
        require(Files.size(jar) == lock.sizeBytes) { "Cached TLA+ Tools size mismatch" }
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)).hex()
        require(digest == lock.sha256) { "Cached TLA+ Tools SHA-256 mismatch" }
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
