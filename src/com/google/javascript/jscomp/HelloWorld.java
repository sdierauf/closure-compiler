package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;

class HelloWorld implements CompilerPass {

  final AbstractCompiler compiler;

  public HelloWorld(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Traversal());
  }
  
  
  private class Traversal extends AbstractPostOrderCallback {

	@Override
	public void visit(NodeTraversal t, Node n, Node parent) {
		if (!n.isVar()) {
			return;
		}
		Node printHelloWorld = IR.call(IR.name("print"), IR.string("hello world"));
		
		Node statement = IR.exprResult(printHelloWorld);
		parent.addChildAfter(statement, n);
		
	}
  }
}