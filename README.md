<!--
   SPDX-License-Identifier: CC-BY-4.0

   Copyright 2019-2024 The TurnKey Authors

   This work is licensed under the Creative Commons Attribution 4.0
   International License.

   You should have received a copy of the license along with this
   work. If not, see <https://creativecommons.org/licenses/by/4.0/>.
-->

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/tudo-aqua/cvc5-turnkey/ci.yml?logo=githubactions&logoColor=white)](https://github.com/tudo-aqua/cvc5-turnkey/actions)
[![JavaDoc](https://javadoc.io/badge2/tools.aqua/cvc5-turnkey/javadoc.svg)](https://javadoc.io/doc/tools.aqua/cvc5-turnkey)
[![Maven Central](https://img.shields.io/maven-central/v/tools.aqua/cvc5-turnkey?logo=apache-maven)](https://search.maven.org/artifact/tools.aqua/cvc5-turnkey)

# The cvc5-TurnKey Distribution

[cvc5](https://github.com/cvc5/cvc5) is a widely used SMT solver that is written in C and C++. The
authors provide a Java API, however, it expects the user to install native libraries for their
platform. This precludes easy usage of the solver as, e.g., a Maven dependency.

This project packages the cvc5 Java API and native libraries for all supported platforms as a
[TurnKey bundle](https://github.com/tudo-aqua/turnkey-support). At runtime, the correct library for
the platform is extracted and automatically loaded. The libraries themselves are modified to support
this paradigm.

At the moment, platform support is as follows:

| Operating System | x86 | AMD64 | AARCH64 |
| ---------------- | :-: | :---: | :-----: |
| Linux            |     |   ✓   |         |
| macOS            |     |   ✓   |    ✓    |
| Windows          |     |   ✓   |         |

## Usage

The library can be included as a regular Maven dependency from the Maven Central repository. See the
page for your version of choice [there](https://search.maven.org/artifact/tools.aqua/cvc5-turnkey)
for configuration snippets for most build tools.

## Building

Building the library requires multiple native tools to be installed that can not be installed using
Gradle:

- For library rewriting, see the tools required by the
  [turnkey-gradle-plugin](https://github.com/tudo-aqua/turnkey-gradle-plugin).
- Additionally, Python 3 is required for a code generation step and localized by the
  [gradle-use-python-plugin](https://github.com/xvik/gradle-use-python-plugin).

## Java and JPMS Support

The library requires Java 8. It can be used as a Java module on Java 9+ via the multi-release JAR
mechanism as the `tools.aqua.turnkey.cvc5` module.

## License

cvc5 itself is licensed under the
[3-Clause BSD License](https://opensource.org/licenses/BSD-3-Clause) and also incorporates
components licensed under the [MIT License](https://opensource.org/licenses/MIT) and the
[GNU Lesser General Public License v3.0 or later](https://www.gnu.org/licenses/lgpl-3.0.en.html).
Two dependencies are introduced: the TurnKey support library is licensed under the
[ISC License](https://opensource.org/licenses/ISC) and the JSpecify annotation library used by it is
licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

This does _not_ bundle a GPL-licensed version of cvc5.

Tests and other non-runtime code are licensed under the
[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). Standalone documentation
is licensed under the
[Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/).
