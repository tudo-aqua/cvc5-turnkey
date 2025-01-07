/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2025 The TurnKey Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.diffplug.gradle.spotless.JavaExtension
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.KotlinGradleExtension
import com.diffplug.gradle.spotless.SpotlessTask
import com.github.gradle.node.variant.computeNodeDir
import com.github.gradle.node.variant.computeNodeExec
import com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.MethodReferenceExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.spotbugs.snom.Effort.MAX
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.plugins.BasePlugin.BUILD_GROUP
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.jvm.toolchain.JvmVendorSpec.ADOPTIUM
import org.gradle.jvm.toolchain.JvmVendorSpec.AZUL
import org.gradle.jvm.toolchain.JvmVendorSpec.BELLSOFT
import org.gradle.jvm.toolchain.JvmVendorSpec.GRAAL_VM
import org.gradle.jvm.toolchain.JvmVendorSpec.MICROSOFT
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import tools.aqua.*
import tools.aqua.turnkey.plugin.*

plugins {
  `java-library`
  `maven-publish`
  pmd
  signing

  alias(libs.plugins.download)
  alias(libs.plugins.moduleInfo)
  alias(libs.plugins.nexusPublish)
  alias(libs.plugins.node)
  alias(libs.plugins.spotbugs)
  alias(libs.plugins.spotless)
  alias(libs.plugins.taskTree)
  alias(libs.plugins.turnkey)
  alias(libs.plugins.versions)
}

group = "tools.aqua"

val cvc5Version = "1.2.0"
val turnkeyVersion = ""

version = if (turnkeyVersion.isNotBlank()) "$cvc5Version.$turnkeyVersion" else cvc5Version

val testToolchains =
    listOf(8, 11, 17, 21).map { Triple("EclipseTemurin$it", it, ADOPTIUM) } +
        listOf(8, 11, 17, 21).map { Triple("AzulZulu$it", it, AZUL) } +
        listOf(8, 11, 17, 21).map { Triple("BellsoftLiberica$it", it, BELLSOFT) } +
        listOf(8, 11, 17, 21).map { Triple("GraalVM$it", it, GRAAL_VM) } +
        listOf(11, 17, 21).map { Triple("MicrosoftOpenJDK$it", it, MICROSOFT) }

repositories { mavenCentral() }

val testJar by configurations.registering { extendsFrom(configurations.testRuntimeClasspath.get()) }

dependencies {
  implementation(libs.turnkey)

  testImplementation(platform(libs.junit))
  testImplementation(libs.assertj)
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.launcher)
  testJar(libs.junit.console)
}

val versionFile by
    tasks.registering {
      doLast { layout.buildDirectory.file("cvc5.version").get().asFile.writeText(cvc5Version) }
    }

node {
  download = true
  workDir = layout.buildDirectory.dir("nodejs")
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

tasks.dependencyUpdates {
  gradleReleaseChannel = "current"
  revision = "release"
  rejectVersionIf { isNonStable(candidate.version) && !isNonStable(currentVersion) }
}

pmd {
  isConsoleOutput = true
  toolVersion = libs.pmd.get().version!!
  ruleSetConfig = resources.text.fromFile("config/pmd.xml")
}

tasks.pmdMain { enabled = false }

spotbugs { effort = MAX }

tasks.spotbugsMain { enabled = false }

spotless {
  format("javaTest", JavaExtension::class.java) {
    target(sourceSets.test.get().java.filter { it.extension == "java" })
    licenseHeaderFile(project.file("config/license/Apache-2.0-cstyle")).updateYearWithLatest(true)
    googleJavaFormat()
  }
  kotlinGradle {
    licenseHeaderFile(
            file("config/license/Apache-2.0-cstyle"),
            "(import |@file|plugins |dependencyResolutionManagement|rootProject.name)")
        .updateYearWithLatest(true)
    ktfmt()
  }
  format("kotlinBuildSrc", KotlinExtension::class.java) {
    target("buildSrc/src/*/kotlin/**/*.kt")
    licenseHeaderFile(
            file("config/license/Apache-2.0-cstyle"),
        )
        .updateYearWithLatest(true)
    ktfmt()
  }
  format("kotlinGradleBuildSrc", KotlinGradleExtension::class.java) {
    target("buildSrc/*.gradle.kts", "buildSrc/src/*/kotlin/**/*.gradle.kts")
    licenseHeaderFile(
            file("config/license/Apache-2.0-cstyle"),
            "(import |@file|plugins |dependencyResolutionManagement|rootProject.name)")
        .updateYearWithLatest(true)
    ktfmt()
  }
  format("markdown") {
    target(".github/**/*.md", "*.md")
    licenseHeaderFile(project.file("config/license/CC-BY-4.0-xmlstyle"), """#+|\[!\[""")
        .updateYearWithLatest(true)
    prettier()
        .npmInstallCache()
        .nodeExecutable(computeNodeExec(node, computeNodeDir(node)).get())
        .config(mapOf("parser" to "markdown", "printWidth" to 100, "proseWrap" to "always"))
  }
  yaml {
    target("config/**/*.yml", ".github/**/*.yml", "CITATION.cff")
    licenseHeaderFile(project.file("config/license/Apache-2.0-hashmark"), "[A-Za-z-]+:")
        .updateYearWithLatest(true)
    prettier()
        .npmInstallCache()
        .nodeExecutable(computeNodeExec(node, computeNodeDir(node)).get())
        .config(mapOf("parser" to "yaml", "printWidth" to 100))
  }
  format("toml") {
    target("gradle/libs.versions.toml")
    licenseHeaderFile(project.file("config/license/Apache-2.0-hashmark"), """\[[A-Za-z-]+]""")
        .updateYearWithLatest(true)
    prettier(mapOf("prettier-plugin-toml" to libs.versions.prettier.toml.get()))
        .npmInstallCache()
        .nodeExecutable(computeNodeExec(node, computeNodeDir(node)).get())
        .config(
            mapOf(
                "plugins" to listOf("prettier-plugin-toml"),
                "parser" to "toml",
                "alignComments" to false,
                "printWidth" to 100,
            ))
  }
}

tasks.withType<SpotlessTask>().configureEach { dependsOn(tasks.npmSetup) }

// cvc5 Source Code

val downloadSource by
    tasks.registering(Download::class) {
      description = "Download the cvc5 source distribution."

      src("https://github.com/cvc5/cvc5/archive/refs/tags/cvc5-$cvc5Version.zip")
      dest(layout.buildDirectory.file("download/source.zip"))
      overwrite(false)
      quiet(true)
    }

val extractSource by
    tasks.registering(Copy::class) {
      description = "Extract the cvc5 source distribution."

      from(zipTree(downloadSource.map { it.dest }))

      into(layout.buildDirectory.dir("unpacked/source"))
    }

val copyNonGeneratedSources by
    tasks.registering(Copy::class) {
      description = "Copy the non-generated cvc5 Java sources to the correct directory structure."

      from(extractSource.map { it.destinationDir.resolve("cvc5-cvc5-$cvc5Version/src/api/java") })
      include("io/github/cvc5/*.java")
      exclude("io/github/cvc5/Utils.java")

      into(layout.buildDirectory.dir("non-generated-sources"))
    }

val generateKinds by
    tasks.registering(CVC5EnumGeneratorTask::class) {
      description = "Generate the Java source for cvc5 kinds."

      sourceDir =
          layout.dir(extractSource.map { it.destinationDir.resolve("cvc5-cvc5-$cvc5Version") })
      header = "cvc5_kind"
      outputDir = layout.buildDirectory.dir("generated/kinds")
    }

val generateProofRules by
    tasks.registering(CVC5EnumGeneratorTask::class) {
      description = "Generate the Java source for cvc5 proof rules."

      sourceDir =
          layout.dir(extractSource.map { it.destinationDir.resolve("cvc5-cvc5-$cvc5Version") })
      header = "cvc5_proof_rule"
      outputDir = layout.buildDirectory.dir("generated/proof-rules")
    }

val generateSkolemIDs by
    tasks.registering(CVC5EnumGeneratorTask::class) {
      description = "Generate the Java source for cvc5 Skolem IDs."

      sourceDir =
          layout.dir(extractSource.map { it.destinationDir.resolve("cvc5-cvc5-$cvc5Version") })
      header = "cvc5_skolem_id"
      outputDir = layout.buildDirectory.dir("generated/skolem-ids")
    }

val generateTypes by
    tasks.registering(CVC5EnumGeneratorTask::class) {
      description = "Generate the Java source for cvc5 types."

      sourceDir =
          layout.dir(extractSource.map { it.destinationDir.resolve("cvc5-cvc5-$cvc5Version") })
      header = "cvc5_types"
      outputDir = layout.buildDirectory.dir("generated/types")
    }

val rewriteUtilsJava by
    tasks.registering(JavaRewriteTask::class) {
      description = "Rewrite the cvc5 native binding to use the new unpack-and-link code."

      inputDirectory =
          extractSource.map { it.destinationDir.resolve("cvc5-cvc5-$cvc5Version/src/api/java") }
      inputFile = "io/github/cvc5/Utils.java"

      rewrite { compilationUnit ->
        val utilsClass = compilationUnit.types.single { it.name.id == "Utils" }
        val loadLibraries =
            utilsClass.methods.first {
              it.isStatic && it.name.id == "loadLibraries" && it.parameters.isEmpty()
            }
        loadLibraries.setBody(
            BlockStmt(
                NodeList(
                    ExpressionStmt(
                        MethodCallExpr(
                            "tools.aqua.turnkey.support.TurnKey.load",
                            StringLiteralExpr("io/github/cvc5"),
                            MethodReferenceExpr(
                                ClassExpr(parseClassOrInterfaceType("io.github.cvc5.Utils")),
                                NodeList(),
                                "getResourceAsStream")),
                    ))))
      }

      outputDirectory = layout.buildDirectory.dir("generated/rewritten-utils")
    }

// Linux AMD64

val downloadLinuxAMD64 by
    tasks.registering(Download::class) {
      description = "Download the cvc5 binary distribution for Linux AMD64."

      src(
          "https://github.com/cvc5/cvc5/releases/download/cvc5-$cvc5Version/cvc5-Linux-x86_64-shared.zip")
      dest(layout.buildDirectory.file("download/linux-amd64.zip"))
      overwrite(false)
      quiet(true)
    }

val extractLinuxAMD64 by
    tasks.registering(Copy::class) {
      description = "Extract libraries from the cvc5 binary distribution for Linux AMD64."

      from(zipTree(downloadLinuxAMD64.map { it.dest }))
      include("*/lib/*.so")
      eachFile { path = name }

      into(layout.buildDirectory.dir("unpacked/linux-amd64"))
    }

val turnkeyLinuxAMD64 by
    tasks.registering(ELFTurnKeyTask::class) {
      description = "Run the TurnKey packager for Linux AMD64"
      dependsOn(extractLinuxAMD64)

      libraries.from(fileTree(extractLinuxAMD64.map { it.destinationDir })).filter { it.isFile }
      rootLibraryNames.add("libcvc5jni.so")
      targetDirectory = layout.buildDirectory.dir("turnkey/linux-amd64")
      targetSubPath = "io/github/cvc5/linux/amd64"
    }

// macOS AARCH64

val downloadMacOSAARCH64 by
    tasks.registering(Download::class) {
      description = "Download the cvc5 binary distribution for macOS AARCH64."

      src(
          "https://github.com/cvc5/cvc5/releases/download/cvc5-$cvc5Version/cvc5-macOS-arm64-shared.zip")
      dest(layout.buildDirectory.file("download/macos-aarch64.zip"))
      overwrite(false)
      quiet(true)
    }

val extractMacOSAARCH64 by
    tasks.registering(Copy::class) {
      description = "Extract libraries from the cvc5 binary distribution for macOS AARCH64."

      from(zipTree(downloadMacOSAARCH64.map { it.dest }))
      include("*/lib/*.dylib")
      eachFile { path = name }

      into(layout.buildDirectory.dir("unpacked/macos-aarch64"))
    }

val turnkeyMacOSAARCH64 by
    tasks.registering(MachOTurnKeyTask::class) {
      description = "Run the TurnKey packager for macOS AARCH64"
      dependsOn(extractMacOSAARCH64)

      libraries.from(fileTree(extractMacOSAARCH64.map { it.destinationDir })).filter { it.isFile }
      rootLibraryNames.add("libcvc5jni.dylib")
      targetDirectory = layout.buildDirectory.dir("turnkey/macos-aarch64")
      targetSubPath = "io/github/cvc5/osx/aarch64"
    }

// macOS AMD64

val downloadMacOSAMD64 by
    tasks.registering(Download::class) {
      description = "Download the cvc5 binary distribution for macOS AMD64."

      src(
          "https://github.com/cvc5/cvc5/releases/download/cvc5-$cvc5Version/cvc5-macOS-x86_64-shared.zip")
      dest(layout.buildDirectory.file("download/macos-amd64.zip"))
      overwrite(false)
      quiet(true)
    }

val extractMacOSAMD64 by
    tasks.registering(Copy::class) {
      description = "Extract libraries from the cvc5 binary distribution for macOS AMD64."

      from(zipTree(downloadMacOSAMD64.map { it.dest }))
      include("*/lib/*.dylib")
      eachFile { path = name }

      into(layout.buildDirectory.dir("unpacked/macos-amd64"))
    }

val turnkeyMacOSAMD64 by
    tasks.registering(MachOTurnKeyTask::class) {
      description = "Run the TurnKey packager for macOS AMD64"
      dependsOn(extractMacOSAMD64)

      libraries.from(fileTree(extractMacOSAMD64.map { it.destinationDir })).filter { it.isFile }
      rootLibraryNames.add("libcvc5jni.dylib")
      targetDirectory = layout.buildDirectory.dir("turnkey/macos-amd64")
      targetSubPath = "io/github/cvc5/osx/amd64"
    }

// Windows AMD64

val downloadWindowsAMD64 by
    tasks.registering(Download::class) {
      description = "Download the cvc5 binary distribution for Windows AMD64."

      src(
          "https://github.com/cvc5/cvc5/releases/download/cvc5-$cvc5Version/cvc5-Win64-x86_64-shared.zip")
      dest(layout.buildDirectory.file("download/windows-amd64.zip"))
      overwrite(false)
      quiet(true)
    }

val extractWindowsAMD64 by
    tasks.registering(Copy::class) {
      description = "Extract libraries from the cvc5 binary distribution for Windows AMD64."

      from(zipTree(downloadWindowsAMD64.map { it.dest }))
      include("*/bin/*.dll") // the libraries for windows are not in lib
      eachFile { path = name }

      into(layout.buildDirectory.dir("unpacked/windows-amd64"))
    }

val turnkeyWindowsAMD64 by
    tasks.registering(COFFTurnKeyTask::class) {
      description = "Run the TurnKey packager for Windows AMD64"
      dependsOn(extractWindowsAMD64)

      libraries.from(fileTree(extractWindowsAMD64.map { it.destinationDir })).filter { it.isFile }
      rootLibraryNames.add("cvc5jni.dll")
      targetDirectory = layout.buildDirectory.dir("turnkey/windows-amd64")
      targetSubPath = "io/github/cvc5/windows/amd64"
    }

sourceSets {
  main {
    java {
      srcDirs(
          copyNonGeneratedSources.map { it.destinationDir },
          generateKinds.map { it.outputDir },
          generateProofRules.map { it.outputDir },
          generateSkolemIDs.map { it.outputDir },
          generateTypes.map { it.outputDir },
          rewriteUtilsJava.flatMap { it.outputDirectory })
    }
    resources {
      srcDir(turnkeyLinuxAMD64.flatMap { it.targetDirectory })
      srcDir(turnkeyMacOSAARCH64.flatMap { it.targetDirectory })
      srcDir(turnkeyMacOSAMD64.flatMap { it.targetDirectory })
      srcDir(turnkeyWindowsAMD64.flatMap { it.targetDirectory })
    }
  }
}

val sourcesJar by
    tasks.registering(Jar::class) {
      group = BUILD_GROUP
      from(sourceSets.main.map { it.allJava }, extractSource.map { it.destinationDir })
      archiveClassifier = DocsType.SOURCES
    }

tasks.assemble { dependsOn(sourcesJar) }

java {
  toolchain { languageVersion = JavaLanguageVersion.of(8) }
  modularity.inferModulePath = false
  withJavadocJar()
  withSourcesJar()
}

val test by testing.suites.existing(JvmTestSuite::class)
val platformTests =
    testToolchains.map { (name, jvmVersion, jvmVendor) ->
      tasks.register<Test>("testOn$name") {
        group = VERIFICATION_GROUP
        javaLauncher =
            project.javaToolchains.launcherFor {
              languageVersion = JavaLanguageVersion.of(jvmVersion)
              vendor = jvmVendor
            }
        useJUnitPlatform()
        testClassesDirs = files(test.map { it.sources.output.classesDirs })
        classpath = files(test.map { it.sources.runtimeClasspath })
        systemProperty("expectedCVC5Version", cvc5Version)
        if (jvmVersion > 8) {
          jvmArgs("--add-opens", "java.base/java.io=ALL-UNNAMED")
        }
        forkEvery = 1 // for hook tests
        testLogging { events(FAILED, STANDARD_ERROR, SKIPPED, PASSED) }
      }
    }

tasks.test {
  dependsOn(*platformTests.toTypedArray())
  exclude("*")
}

tasks.javadoc {
  // we are using a Java 8 toolchain, so javadoc does not know about modules
  exclude("module-info.java")
  (options as? StandardJavadocDocletOptions)?.apply {
    links("https://docs.oracle.com/javase/8/docs/api/")
    // disable doclint -- the cvc5 JavaDoc contains invalid tags.
    addBooleanOption("Xdoclint:none", true)
    links("https://docs.oracle.com/javase/8/docs/api/")
  }
}

tasks.compileModuleInfo {
  moduleVersion = version.toString()
  targetFile = layout.buildDirectory.file("mic/META-INF/versions/9/module-info.class")
}

tasks.jar {
  from(layout.buildDirectory.dir("mic"))
  manifest { attributes("Multi-Release" to "True") }
}

val testRunner by
    tasks.registering(Jar::class) {
      group = BUILD_GROUP

      destinationDirectory = layout.buildDirectory.dir("libs")
      archiveClassifier = "test-runner"

      from(
          tasks.compileJava.map { it.destinationDirectory },
          sourceSets.main.map { it.resources.sourceDirectories },
          tasks.compileTestJava.map { it.destinationDirectory },
          sourceSets.test.map { it.resources.sourceDirectories },
          testJar.map { cp -> cp.files.map { if (it.isDirectory) it else zipTree(it) } },
      )
      duplicatesStrategy = EXCLUDE
      manifest { attributes("Main-Class" to "org.junit.platform.console.ConsoleLauncher") }
    }

publishing {
  publications {
    register<MavenPublication>("maven") {
      from(components["java"])
      artifactId = "cvc5-turnkey"
      pom {
        name = "cvc5-TurnKey"
        description = "TurnKey artifact for cvc5"
        url = "https://github.com/tudo-aqua/cvc5-turnkey"
        licenses {
          license {
            // cvc5, SymFPU
            name = "The 3-Clause BSD License"
            url = "https://opensource.org/license/BSD-3-Clause"
          }
          license {
            // CaDiCal
            name = "The MIT License"
            url = "https://opensource.org/licenses/MIT"
          }
          license {
            // GMP, LibPoly
            name = "GNU Lesser General Public License v3.0 or later"
            url = "https://www.gnu.org/licenses/lgpl-3.0.en.html"
          }
        }
        developers {
          developer {
            name = "Simon Dierl"
            email = "simon.dierl@tu-dortmund.de"
            organization = "AQUA Group, Department of Computer Science, TU Dortmund University"
            organizationUrl = "https://aqua.engineering/"
          }
        }
        scm {
          connection = "scm:git:https://github.com:tudo-aqua/cvc5-turnkey.git"
          developerConnection = "scm:git:ssh://git@github.com:tudo-aqua/cvc5-turnkey.git"
          url = "https://github.com/tudo-aqua/cvc5-turnkey/tree/main"
        }
      }
    }
  }
}

signing {
  setRequired {
    gradle.taskGraph.allTasks.filterIsInstance<PublishToMavenRepository>().isNotEmpty()
  }
  useGpgCmd()
  sign(publishing.publications["maven"])
}

nexusPublishing { this.repositories { sonatype() } }
