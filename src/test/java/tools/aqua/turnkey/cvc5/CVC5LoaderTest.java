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

package tools.aqua.turnkey.cvc5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.github.cvc5.Solver;
import io.github.cvc5.TermManager;
import org.junit.jupiter.api.Test;

/** Test that cvc5 native methods can successfully be invoked. */
class CVC5LoaderTest extends CVC5AutoCleaner {

  /** Invoke the {@link Solver#getVersion()} method from cvc5. */
  @Test
  void testLoading() {
    final String expectedVersion = System.getProperty("expectedCVC5Version");
    assumeThat(expectedVersion).isNotNull();

    final String version = new Solver(new TermManager()).getVersion();
    assertThat(version).contains(expectedVersion);
  }
}
