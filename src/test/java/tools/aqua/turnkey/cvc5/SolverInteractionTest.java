/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2024 The TurnKey Authors
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

import static io.github.cvc5.Kind.AND;
import static io.github.cvc5.Kind.DIVISION;
import static io.github.cvc5.Kind.EQUAL;
import static io.github.cvc5.Kind.GEQ;
import static io.github.cvc5.Kind.GT;
import static io.github.cvc5.Kind.LEQ;
import static io.github.cvc5.Kind.LT;
import static io.github.cvc5.Kind.MULT;
import static io.github.cvc5.Kind.STRING_CONCAT;
import static io.github.cvc5.Kind.TO_REAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BOOLEAN;

import io.github.cvc5.CVC5ApiException;
import io.github.cvc5.Result;
import io.github.cvc5.Solver;
import io.github.cvc5.Term;
import io.github.cvc5.TermManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Test more complicated solver interactions that require the entire ecosystem to be loaded. */
class SolverInteractionTest extends CVC5AutoCleaner {

  /** Check the satisfiability of a simple comparison. */
  @Test
  void testSimpleSolving() {
    final TermManager termManager = new TermManager();

    final Term x = termManager.mkConst(termManager.getIntegerSort(), "x");
    final Term c = termManager.mkInteger(15);
    final Term b = termManager.mkTerm(GT, x, c);

    final Solver solver = new Solver(termManager);
    solver.setOption("produce-models", "true");

    assertThat(solver.checkSatAssuming(b)).extracting(Result::isSat, BOOLEAN).isTrue();

    final Term evaluated = solver.getValue(b);
    assertThat(Optional.of(evaluated)).get().extracting(Term::getBooleanValue, BOOLEAN).isTrue();
  }

  /** Check the satisfiability of two floating-point expressions. */
  @Test
  void testArithmeticSolving() throws CVC5ApiException {
    final TermManager termManager = new TermManager();

    final Term x = termManager.mkConst(termManager.getIntegerSort(), "x");
    final Term xReal = termManager.mkTerm(TO_REAL, x);

    final Term y = termManager.mkConst(termManager.getRealSort(), "y");

    final Term three = termManager.mkReal(3);
    final Term minusTwo = termManager.mkReal(-2);
    final Term twoThirds = termManager.mkReal(2, 3);

    final Term threeY = termManager.mkTerm(MULT, three, y);
    final Term yOverX = termManager.mkTerm(DIVISION, y, xReal);

    final Term xGreaterEqualThreeY = termManager.mkTerm(GEQ, xReal, threeY);
    final Term xLessEqualY = termManager.mkTerm(LEQ, x, y);
    final Term minusTwoLessX = termManager.mkTerm(LT, minusTwo, xReal);

    final Term assumptions =
        termManager.mkTerm(AND, xGreaterEqualThreeY, xLessEqualY, minusTwoLessX);

    final Solver solver = new Solver(termManager);
    solver.assertFormula(assumptions);

    solver.push();
    final Term differenceLessEqualTwoThirds = termManager.mkTerm(LEQ, yOverX, twoThirds);
    assertThat(solver.checkSatAssuming(differenceLessEqualTwoThirds))
        .extracting(Result::isSat, BOOLEAN)
        .isTrue();
    solver.pop();

    solver.push();
    final Term differenceIsTwoThirds = termManager.mkTerm(EQUAL, yOverX, twoThirds);
    assertThat(solver.checkSatAssuming(differenceIsTwoThirds))
        .extracting(Result::isSat, BOOLEAN)
        .isTrue();
    solver.pop();
  }

  /** Check that expression concatenation operates correctly. */
  @Test
  void testConcat() {
    final TermManager termManager = new TermManager();

    final Term x = termManager.mkString("x");
    final Term y = termManager.mkString("y");
    final Term xPlusY = termManager.mkTerm(STRING_CONCAT, x, y);
    final Term xy = termManager.mkString("xy");

    final Term b = termManager.mkTerm(EQUAL, xPlusY, xy);

    final Solver solver = new Solver(termManager);

    assertThat(solver.checkSatAssuming(b)).extracting(Result::isSat, BOOLEAN).isTrue();
  }
}
