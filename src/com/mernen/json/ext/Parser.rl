package com.mernen.json.ext;

import java.util.Arrays;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

public class Parser extends RubyObject {
	private IRubyObject vSource;
	private int sourcePtr;
	private int len;
	private ByteList memo;
	private IRubyObject createId;
	private int maxNesting;
	private int currentNesting;
	private boolean allowNaN;

	private final RubyModule jsonModule;
	private final RubyClass parserErrorClass;
	private final RubyClass nestingErrorClass;
	private final IRubyObject NAN;
	private final IRubyObject INFINITY;
	private final IRubyObject MINUS_INFINITY;

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
	}

	public Parser(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);

		jsonModule = runtime.getModule("JSON");

		parserErrorClass = jsonModule.getClass("ParserError");
		nestingErrorClass = jsonModule.getClass("NestingError");

		NAN = jsonModule.getConstant("NaN");
		INFINITY = jsonModule.getConstant("Infinity");
		MINUS_INFINITY = jsonModule.getConstant("MinusInfinity");
	}

	@JRubyMethod(name = "new", required = 1, optional = 1, meta = true)
	public static IRubyObject newInstance(IRubyObject clazz, IRubyObject[] args, Block block) {
		Parser parser = (Parser)((RubyClass)clazz).allocate();

		parser.callInit(args, block);

		return parser;
	}

	@JRubyMethod(name = "initialize", required = 1, optional = 1,
	             visibility = Visibility.PRIVATE)
	public IRubyObject initialize(IRubyObject[] args) {
		RubyString source = args[0].convertToString();
		int len = source.getByteList().length();

		if (len < 2) {
			throw new RaiseException(getRuntime(), parserErrorClass,
				"A JSON text must at least contain two octets!", false);
		}

		ThreadContext context = getRuntime().getCurrentContext();
		if (args.length > 1) {
			RubyHash opts = args[1].convertToHash();

			IRubyObject maxNesting = getSymItem(opts, "max_nesting");
			this.maxNesting = maxNesting == null ? 0 : RubyNumeric.fix2int(maxNesting);

			IRubyObject allowNaN = getSymItem(opts, "allow_nan");
			this.allowNaN = allowNaN != null && allowNaN.isTrue();

			IRubyObject createAdditions = getSymItem(opts, "create_additions");
			this.createId = createAdditions == null || createAdditions.isTrue() ?
				jsonModule.callMethod(context, "create_id") :
				getRuntime().getNil();
		}
		else {
			this.maxNesting = 19;
			this.allowNaN = false;
			this.createId = jsonModule.callMethod(context, "create_id");
		}

		this.currentNesting = 0;

		this.len = len;
		this.sourcePtr = 0;
		this.vSource = source;

		return this;
	}

	private IRubyObject getSymItem(RubyHash hash, String key) {
		return hash.fastARef(RubySymbol.newSymbol(getRuntime(), key));
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
