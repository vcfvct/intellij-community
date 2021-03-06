/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Represents a loop of form {@code for(int/long counter = initializer; counter </<= bound; counter++)}
 *
 * @author Tagir Valeev
 */
public class CountingLoop {
  final @NotNull PsiLocalVariable myCounter;
  final @NotNull PsiLoopStatement myLoop;
  final @NotNull PsiExpression myInitializer;
  final @NotNull PsiExpression myBound;
  final boolean myIncluding;

  private CountingLoop(@NotNull PsiLoopStatement loop,
                       @NotNull PsiLocalVariable counter,
                       @NotNull PsiExpression initializer,
                       @NotNull PsiExpression bound,
                       boolean including) {
    myInitializer = initializer;
    myCounter = counter;
    myLoop = loop;
    myBound = bound;
    myIncluding = including;
  }

  /**
   * @return loop counter variable
   */
  @NotNull
  public PsiLocalVariable getCounter() {
    return myCounter;
  }

  /**
   * @return loop statement
   */
  @NotNull
  public PsiLoopStatement getLoop() {
    return myLoop;
  }

  /**
   * @return counter variable initial value
   */
  @NotNull
  public PsiExpression getInitializer() {
    return myInitializer;
  }

  /**
   * @return loop bound
   */
  @NotNull
  public PsiExpression getBound() {
    return myBound;
  }

  /**
   * @return true if bound is including
   */
  public boolean isIncluding() {
    return myIncluding;
  }

  @Nullable
  public static CountingLoop from(PsiForStatement forStatement) {
    // check that initialization is for(int/long i = <initial_value>;...;...)
    PsiDeclarationStatement initialization = tryCast(forStatement.getInitialization(), PsiDeclarationStatement.class);
    if (initialization == null || initialization.getDeclaredElements().length != 1) return null;
    PsiLocalVariable counter = tryCast(initialization.getDeclaredElements()[0], PsiLocalVariable.class);
    if(counter == null) return null;
    if(!counter.getType().equals(PsiType.INT) && !counter.getType().equals(PsiType.LONG)) return null;

    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(counter.getInitializer());
    if(initializer == null) return null;

    // check that increment is like for(...;...;i++)
    if(!VariableAccessUtils.variableIsIncremented(counter, forStatement.getUpdate())) return null;

    // check that condition is like for(...;i<bound;...) or for(...;i<=bound;...)
    PsiBinaryExpression condition = tryCast(forStatement.getCondition(), PsiBinaryExpression.class);
    if(condition == null) return null;
    IElementType type = condition.getOperationTokenType();
    boolean closed = false;
    PsiExpression bound;
    PsiExpression ref;
    if(type.equals(JavaTokenType.LE)) {
      bound = condition.getROperand();
      ref = condition.getLOperand();
      closed = true;
    } else if(type.equals(JavaTokenType.LT)) {
      bound = condition.getROperand();
      ref = condition.getLOperand();
    } else if(type.equals(JavaTokenType.GE)) {
      bound = condition.getLOperand();
      ref = condition.getROperand();
      closed = true;
    } else if(type.equals(JavaTokenType.GT)) {
      bound = condition.getLOperand();
      ref = condition.getROperand();
    } else return null;
    if(bound == null || !ExpressionUtils.isReferenceTo(ref, counter)) return null;
    if(!TypeConversionUtil.areTypesAssignmentCompatible(counter.getType(), bound)) return null;
    return new CountingLoop(forStatement, counter, initializer, bound, closed);
  }
}
