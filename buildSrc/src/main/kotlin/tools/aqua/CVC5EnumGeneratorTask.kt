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

package tools.aqua

import kotlin.io.path.createDirectories
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import ru.vyarus.gradle.plugin.python.cmd.Python
import ru.vyarus.gradle.plugin.python.cmd.env.SimpleEnvironment

/** Run the CVC5 enum generator script. */
abstract class CVC5EnumGeneratorTask : DefaultTask() {

  /** The CVC5 source checkout root. */
  @get:InputDirectory abstract val sourceDir: DirectoryProperty

  /** The name of the C header to generate enums from, without path or `.h`. */
  @get:Input abstract val header: Property<String>

  /** The name of the Java package to target for generation. Defaults to `io.github.cvc5`. */
  @get:Input abstract val targetOutputPackage: Property<String>

  init {
    @Suppress("LeakingThis") targetOutputPackage.convention("io.github.cvc5")
  }

  /** The output base directory (i.e., the relative resource root). */
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  /** Transform a Java package name into the corresponding file path. */
  private fun String.packagePath(): String = replace(".", "/")

  /** Run the generator script according to the configuration. */
  @TaskAction
  fun runGenerator() {
    val script = sourceDir.file("src/api/java/genenums.py.in")
    val enumsHeader = sourceDir.file("include/cvc5/${header.get()}.h")

    val shortenedPackageOutputDir =
        outputDir.dir(targetOutputPackage.get().substringBeforeLast(".").packagePath())

    val options =
        listOf(
            "--enums-header",
            enumsHeader.get().toString(),
            "--java-api-path",
            shortenedPackageOutputDir.get().toString())

    shortenedPackageOutputDir.get().asFile.toPath().createDirectories()

    Python(SimpleEnvironment(project))
        .environment("PYTHONPATH", sourceDir.dir("src/api").get().toString())
        .exec((listOf("-B", script.get()) + options).toTypedArray())
  }
}
