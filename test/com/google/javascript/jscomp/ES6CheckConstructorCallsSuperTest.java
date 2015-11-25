package com.google.javascript.jscomp;

public class Es6CheckConstructorCallsSuperTest extends Es6CompilerTestCase {
	  
	@Override
	protected CompilerPass getProcessor(Compiler compiler) {
		// TODO Auto-generated method stub
		return new Es6CheckConstructorCallsSuper(compiler);
	}
	
	public void testCompilerProducesError() {
		System.out.println("should produce error");
		String js = ""
//				+ "class A {"
//				+ "  constructor() {"
//				+ "    this.a = 'a';"
//				+ "  } "
//				+ "}"
				+ "class B extends A {"
				+ "  constructor() {"
				       // should call super here
				+ "    this.b = 'b';"
				+ "  }"
				+ "}";
		testErrorEs6(js, Es6CheckConstructorCallsSuper.NO_SUPER_CALL);
	}
//	
	public void testCompilerSHOULDFAIL() {
		System.out.println("should not produce error");
		String js = ""
//				+ "class A {"
//				+ "  constructor() {"
//				+ "    this.a = 'a'"
//				+ "  } "
//				+ "}"
				+ "class B extends A {"
				+ "  constructor() {"
				+ "    super();"
				+ "  }"
				+ "}";
		testErrorEs6(js, Es6CheckConstructorCallsSuper.NO_SUPER_CALL);
	}
	
//	
//	public void testCompilerSHOULDFAIL_CF() {
//		String js = ""
//				+ "class A {"
//				+ "  constructor() {"
//				+ "    this.a = 'a'"
//				+ "  } "
//				+ "}"
//				+ "class B extends A {"
//				+ "  constructor() {"
//				+ "    if (true) {"
//				+ "      super();"
//				+ "    } else {"
//				+ "      super();"
//				+ "    }"
//				+ "    this.b = 'b';"
//				+ "  }"
//				+ "}";
//		testErrorEs6(js, Es6CheckConstructorCallsSuper.NO_SUPER_CALL);
//	}
	

}
