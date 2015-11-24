/**
 * 
 */
package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;

/**
 * @author sdierauf
 *
 */
public final class ES6CheckConstructorCallsSuper implements CompilerPass, NodeTraversal.Callback {
	
	static final DiagnosticType NO_SUPER_CALL = DiagnosticType.error(
		      "JSC_NO_SUPER_CALL",
		      "An ES6 class that extends a parent must call super in its constructor");
	
	private final AbstractCompiler compiler;
	private Node constructorMember;
	
	public ES6CheckConstructorCallsSuper(AbstractCompiler compiler) {
		this.compiler = compiler;
		this.constructorMember = null;
	}
	
	@Override
	public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
		System.out.println("calling shouldTraverse!");
		// Should traverse if it's a class, extends a parent, and has a constructor
		if (n.isClass()) {
		    Node superClass = n.getFirstChild().getNext();
		    if (superClass.isEmpty()) {
				System.out.println("returning false");
		    	return false;
		    }
			for (Node member = n.getLastChild().getFirstChild();
					member != null;
					member = member.getNext()) {
				if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
					// found constructor
					this.constructorMember = member;
					System.out.println("returning true!");
					return true;
				}
			}
		}
		System.out.println("n wasnt a class");
		return false;
	}

	@Override
	public void visit(NodeTraversal t, Node n, Node parent) {
		// check if constructorMember has a super node
		// if visiting and constructorMember is still null, error
		if (this.constructorMember == null) {
			compiler.report(JSError.make(n, NO_SUPER_CALL));
		}
		if (!this.constructorMember.getLastChild().getFirstChild().isSuper()) {
			compiler.report(JSError.make(n, NO_SUPER_CALL));
		}
		
	}

	@Override
	public void process(Node externs, Node root) {
		NodeTraversal.traverseEs6(compiler, root, this);
	}

}
