/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;

/**
 * A pass for deleting methods that only make a super call with no change in arguments. This pass is
 * useful for cleaning up methods that may become super-only calls after other optimizations have
 * run, such as removal of casts or assertions. This pass must run after those optimizations, {@link
 * ProcessClosurePrimitives}, {@link Es6ConvertSuper}, and type checking.
 */
public final class RemoveSuperMethodsPass implements CompilerPass {

  /** Marker for recognizing super calls converted by {@link ProcessClosurePrimitives}. */
  private static final String SUPERCLASS_MARKER = "\\.superClass_\\.";

  private static final String PROTOTYPE_MARKER = "\\.prototype\\.";

  private final AbstractCompiler compiler;

  public RemoveSuperMethodsPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new RemoveSuperMethodsCallback());
  }

  private class RemoveSuperMethodsCallback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // must be function assignment where the function body has only one statement
      if (n.isFunction() && parent.isAssign() && parent.getParent().isExprResult()) {
        // TODO(moz): Removing methods annotated with @wizaction have caused internal breakage.
        // Figure out what {@link WizPass} might have done wrong and maybe remove this bailout.
        if (parent.getJSDocInfo() != null && parent.getJSDocInfo().isWizaction()) {
          return;
        }
        Node block = n.getLastChild();
        if (!block.hasOneChild()) {
          return;
        }
        Node statement = block.getFirstChild();
        if ((statement.isExprResult() || statement.isReturn())
            && statement.hasOneChild()
            && statement.getFirstChild().isCall()) {
          String methodName = NodeUtil.getName(n);
          if (methodName == null) {
            return;
          }
          Node call = statement.getFirstChild();
          if (argumentsMatch(n, call)
              && returnMatches(call)
              && functionNameMatches(methodName, call)) {
            parent.getParent().detachFromParent();
            compiler.reportCodeChange();
          }
        }
      }
    }

    /**
     * Returns true if the call has the same name as the enclosing function and the call occurs on
     * the superclass.
     *
     * @param enclosingMethodName qualified name of the method to examine.
     * @param call the CALL node inside the function.
     */
    private boolean functionNameMatches(String enclosingMethodName, Node call) {
      String callName = call.getFirstChild().getQualifiedName();
      if (callName == null) {
        return false;
      }

      String[] methodNameSplitted = enclosingMethodName.split(PROTOTYPE_MARKER);
      if (methodNameSplitted.length != 2) {
        return false;
      }
      // The enclosing method name will have the form ns.Foo.prototype.bar
      // The CALL should either be ns.Foo.superClass_.bar.call or ns.FooBase.prototype.bar.call
      String enclosingClassName = methodNameSplitted[0];
      String shortMethodNameConcatedWithCall = methodNameSplitted[1].concat(".call");

      String[] callNameSplittedBySuperClassMarker = callName.split(SUPERCLASS_MARKER);
      if (callNameSplittedBySuperClassMarker.length == 2
          && callNameSplittedBySuperClassMarker[0].equals(enclosingClassName)
          && callNameSplittedBySuperClassMarker[1].equals(shortMethodNameConcatedWithCall)) {
        // matched superClass_ marker generated by ProcessClosurePrimitives. No further
        // name checks needed.
        return true;
      }

      String[] callNameSplittedByPrototypeMarker = callName.split(PROTOTYPE_MARKER);
      if (callNameSplittedByPrototypeMarker.length != 2
          || !callNameSplittedByPrototypeMarker[1].equals(shortMethodNameConcatedWithCall)) {
        return false;
      }

      // Check that call references the superclass
      String calledClass = callNameSplittedByPrototypeMarker[0];
      JSType subclassType = compiler.getTypeIRegistry().getType(enclosingClassName);
      JSType calledClassType = compiler.getTypeIRegistry().getType(calledClass);
      if (subclassType == null || calledClassType == null) {
        return false;
      }
      if (subclassType.toObjectType() == null
          || subclassType.toObjectType().getConstructor() == null) {
        return false;
      }
      FunctionType superClassConstructor =
          subclassType.toObjectType().getConstructor().getSuperClassConstructor();
      // TODO(moz): Investigate why this could be null
      if (superClassConstructor == null) {
        return false;
      }
      return superClassConstructor.getInstanceType().equals(calledClassType);
    }

    private boolean returnMatches(Node call) {
      // no match if function being called does not have function type
      JSType childType = call.getFirstChild().getJSType();
      if (childType == null || !childType.isFunctionType()) {
        return false;
      }
      // no match if function being called has a return value, but result of the call is not part
      // of return statement
      JSType returnType = childType.toMaybeFunctionType().getReturnType();
      if (returnType != null && !returnType.isVoidType() && !returnType.isUnknownType()) {
        return call.getParent().isReturn();
      }
      return true;
    }

    /** Returns true if the arguments of the call are the parameters of the enclosing method */
    private boolean argumentsMatch(Node enclosingMethod, Node call) {
      Node paramList = enclosingMethod.getSecondChild();

      // The CALL should have 2 more children than the param list. The first is the
      // function being called, the second is a THIS argument.
      int numExtraCallChildren = 2;
      if (paramList.getChildCount() + numExtraCallChildren != call.getChildCount()) {
        return false;
      }
      if (!call.getSecondChild().isThis()) {
        return false;
      }
      // The param list should exactly match the remaining arguments to CALL
      for (int i = 0; i < paramList.getChildCount(); i++) {
        Node callArg = call.getChildAtIndex(i + numExtraCallChildren);
        if (!callArg.isName()) {
          return false;
        }
        if (!paramList.getChildAtIndex(i).matchesQualifiedName(callArg.getString())) {
          return false;
        }
      }
      return true;
    }
  }
}
