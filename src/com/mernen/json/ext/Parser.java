// line 1 "Parser.rl"
package com.mernen.json.ext;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

public class Parser extends RubyObject {
	private IRubyObject vSource;
	private char[] source;
	private int len;
	private char[] memo;
	private IRubyObject createId;
	private int maxNesting;
	private int currentNesting;
	private boolean allowNaN;

	private static RubyClass parserErrorClass;
	private static RubyClass nestingErrorClass;
	private static IRubyObject NAN;
	private static IRubyObject INFINITY;
	private static IRubyObject MINUS_INFINITY;

	private static final ObjectAllocator PARSER_ALLOCATOR = new ObjectAllocator() {
		public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
			return new Parser(runtime, klazz);
		}
	};

	static void load(Ruby runtime) {
		runtime.getLoadService().require("json/common");

		RubyModule jsonModule = runtime.defineModule("JSON");
		RubyModule jsonExtModule = runtime.defineModuleUnder("Ext", jsonModule);
		RubyClass parserClass = runtime.defineClassUnder("Parser", runtime.getObject(),
		                                                 PARSER_ALLOCATOR, jsonExtModule);
		parserClass.defineAnnotatedMethods(Parser.class);

		parserErrorClass = (RubyClass)runtime.getClassFromPath("JSON::ParserError");
		nestingErrorClass = (RubyClass)runtime.getClassFromPath("JSON::NestingError");

		NAN = jsonModule.getConstant("NaN");
		INFINITY = jsonModule.getConstant("Infinity");
		MINUS_INFINITY = jsonModule.getConstant("MinusInfinity");
	}

	public Parser(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(name = "new", required = 1, meta = true)
	public static IRubyObject newInstance(IRubyObject clazz, IRubyObject arg) {
		Parser parser = (Parser)((RubyClass)clazz).allocate();

		parser.callInit(new IRubyObject[] {arg}, Block.NULL_BLOCK);

		return parser;
	}

	@JRubyMethod(name = "initialize", required = 1, optional = 1, frame = true,
	             visibility = Visibility.PRIVATE)
	public IRubyObject initialize(IRubyObject[] args) {
		callSuper(getRuntime().getCurrentContext(), new IRubyObject[0], Block.NULL_BLOCK);

		RubyString source = args[0].convertToString();
		int ptr = 0;
		if (source.getByteList().length() < 2) {
			throw new RaiseException(getRuntime(), parserErrorClass,
			                         "A JSON text must at least contain two octets!", false);
		}

		// TODO
		return getRuntime().getNil();
	}

    @JRubyMethod(name = "parse")
	public IRubyObject parse() {
		return RubyArray.newArray(getRuntime());
	}

	@JRubyMethod(name = "source")
	public IRubyObject getSource() {
		return vSource;
	}
}
