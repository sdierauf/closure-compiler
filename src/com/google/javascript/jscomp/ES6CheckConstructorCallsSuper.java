/**
 * 
 */
package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CheckPathsBetweenNodes;
import com.google.javascript.jscomp.ControlFlowGraph;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.HotSwapCompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;

/**
 * @author sdierauf
 *
 */
public final class Es6CheckConstructorCallsSuper implements CompilerPass, NodeTraversal.Callback {

	static final DiagnosticType NO_SUPER_CALL = DiagnosticType.error("JSC_NO_SUPER_CALL",
			"An ES6 class that extends a parent must call super in its constructor");

	private final AbstractCompiler compiler;
	private Node constructorMember;

	public Es6CheckConstructorCallsSuper(AbstractCompiler compiler) {
		this.compiler = compiler;
		this.constructorMember = null;
	}

	@Override
	public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
		return true;
		// System.out.println("calling shouldTraverse!");
		// // Should traverse if it's a class, extends a parent, and has a
		// constructor
		// if (n.isClass()) {
		// Node superClass = n.getFirstChild().getNext();
		// if (superClass == null || superClass.isEmpty()) {
		// System.out.println("returning false");
		// return false;
		// }
		// for (Node member = n.getLastChild().getFirstChild();
		// member != null;
		// member = member.getNext()) {
		// if (member.isMemberFunctionDef() &&
		// member.getString().equals("constructor")) {
		// // found constructor
		// this.constructorMember = member;
		// System.out.println("returning true!");
		// return true;
		// }
		// }
		// }
		// System.out.println("n wasnt a class");
		// return false;
	}

	@Override
	public void visit(NodeTraversal t, Node n, Node parent) {
		boolean hasSuperClass = false;
		boolean hasConstructor = false;
//		if (n.isClass()) {
//			System.out.println("yay found a class");
//			Node superClass = n.getFirstChild().getNext();
//			if (superClass == null || superClass.isEmpty()) {
//				System.out.println("had no super class");
//			} else {
//				hasSuperClass = true;
//				System.out.println("had a super class!");
//			}
//			for (Node member = n.getLastChild().getFirstChild(); member != null; member = member.getNext()) {
//				if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
//					// found constructor
//					hasConstructor = true;
//				}
//			}
//		}
		if (n.isSuper()) {
			System.out.println("yay ayayayayaya a super()!!!");
		}
		if (n.isMemberFunctionDef() && n.getString().equals("constructor")) {
			System.out.println(n);
			System.out.println(t.getControlFlowGraph().toString());
			if (!callsSuperInAllPaths(t.getControlFlowGraph())) {
				System.out.println("had no super call!");
				compiler.report(JSError.make(n, NO_SUPER_CALL));
			} else {
				System.out.println("had a super call");
			}
		}

//		if (hasSuperClass && hasConstructor) {
//			
//		}

	}

	private static final Predicate<Node> SUPER_CALL_PREDICATE = new Predicate<Node>() {
		@Override
		public boolean apply(Node input) {
			System.out.println(input);
			if (input != null) {
				if (input.isSuper()) {
					System.out.println("found the super call yayayayayay");
					return true;
				}
			} else {
				System.out.println("input was null");
			}
			return false;
		}
	};

	public static boolean callsSuperInAllPaths(ControlFlowGraph<Node> graph) {
		CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> test = new CheckPathsBetweenNodes<>(graph,
		        graph.getEntry(), graph.getImplicitReturn(), SUPER_CALL_PREDICATE,
		        Predicates.<DiGraphEdge<Node, ControlFlowGraph.Branch>>alwaysTrue());
		return test.allPathsSatisfyPredicate();
	}

	@Override
	public void process(Node externs, Node root) {
		NodeTraversal.traverseEs6(compiler, root, this);
	}

}
