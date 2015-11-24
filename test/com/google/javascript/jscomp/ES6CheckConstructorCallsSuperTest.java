package com.google.javascript.jscomp;

public class ES6CheckConstructorCallsSuperTest extends Es6CompilerTestCase {
	  
	@Override
	protected CompilerPass getProcessor(Compiler compiler) {
		// TODO Auto-generated method stub
		return new ES6CheckConstructorCallsSuper(compiler);
	}
	
	public void testCompilerProducesError() {
		String js = ""
				+ "class A {"
				+ "  constructor() {"
				+ "    this.a = 'a'"
				+ "  } "
				+ "}"
				+ "class B extends A {"
				+ "  constructor() {"
				+ "    this.b = 'b'"
				+ "  }"
				+ "}";
		testErrorEs6(js, ES6CheckConstructorCallsSuper.NO_SUPER_CALL);
	}
	

}
