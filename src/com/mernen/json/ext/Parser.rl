package com.mernen.json.ext;

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.rl
import java.util.Arrays;
=======
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
>>>>>>> Reimplemented stringUnescape to use Ruby strings and encode \u escapes as UTF-8:src/com/mernen/json/ext/Parser.rl

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.rl
import org.jruby.RubyException;
=======
import org.jruby.RubyFloat;
>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.rl
import org.jruby.RubyHash;
import org.jruby.RubyInteger;
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

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.rl
=======
	private static final int EVIL = 0x666;
	private static final String JSON_MINUS_INFINITY = "-Infinity";

>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.rl
	private static final ObjectAllocator PARSER_ALLOCATOR = new ObjectAllocator() {
		public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
			return new Parser(runtime, klazz);
		}
	};

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.rl
=======
	static class ParserResult {
		IRubyObject result;
		int p;

		ParserResult(IRubyObject result, int p) {
			this.result = result;
			this.p = p;
		}
	}

>>>>>>> Added string support:src/com/mernen/json/ext/Parser.rl
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

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.rl
=======
	private RaiseException unexpectedToken(int start, int end) {
		return new RaiseException(getRuntime(), parserErrorClass,
			"unexpected token at '" + source.subSequence(start, end) + "'", false);
	}

	%%{
		machine JSON_value;
		include JSON_common;

		write data;

		action parse_null {
			result = getRuntime().getNil();
		}
		action parse_false {
			result = getRuntime().getFalse();
		}
		action parse_true {
			result = getRuntime().getTrue();
		}
		action parse_nan {
			if (allowNaN) {
				result = NAN;
			}
			else {
				throw unexpectedToken(p - 2, pe);
			}
		}
		action parse_infinity {
			if (allowNaN) {
				result = INFINITY;
			}
			else {
				throw unexpectedToken(p - 8, pe);
			}
		}
		action parse_number {
			if (pe > fpc + 9 && source.subSequence(fpc, fpc + 9).toString().equals(JSON_MINUS_INFINITY)) {
				if (allowNaN) {
					result = MINUS_INFINITY;
					fexec p + 9 /*+1*/;
					fbreak;
				}
				else {
					throw unexpectedToken(p, pe);
				}
			}
			ParserResult res = parseFloat(data, fpc, pe);
			if (res != null) {
				result = res.result;
				fexec res.p - 1 /*+1*/;
			}
			res = parseInteger(data, fpc, pe);
			if (res != null) {
				result = res.result;
				fexec res.p - 1 /*+1*/;
			}
			fbreak;
		}
		action parse_string {
			ParserResult res = parseString(data, fpc, pe);
			if (res == null) {
				fbreak;
			}
			else {
				result = res.result;
				fexec res.p - 1 /*+1*/;
			}
		}
		action parse_array {
			currentNesting++;
			ParserResult res = parseArray(data, fpc, pe);
			currentNesting--;
			if (res == null) {
				fbreak;
			}
			else {
				result = res.result;
				fexec res.p;
			}
		}
		action exit {
			fbreak;
		}

		main := ( Vnull @parse_null |
		          Vfalse @parse_false |
		          Vtrue @parse_true |
		          VNaN @parse_nan |
		          VInfinity @parse_infinity |
		          begin_number >parse_number |
		          begin_string >parse_string |
		          begin_array >parse_array
		        ) %*exit;
	}%%

	ParserResult parseValue(byte[] data, int p, int pe) {
		int cs = EVIL;
		IRubyObject result = null;

		%% write init;
		%% write exec;

		if (cs >= JSON_value_first_final && result != null) {
			return new ParserResult(result, p);
		}
		else {
			return null;
		}
	}

	%%{
		machine JSON_integer;

		write data;

		action exit { fbreak; }

		main := '-'? ( '0' | [1-9][0-9]* ) ( ^[0-9] @exit );
	}%%

	ParserResult parseInteger(byte[] data, int p, int pe) {
		int cs = EVIL;

		%% write init;
		int memo = p;
		%% write exec;

		if (cs >= JSON_integer_first_final) {
			RubyString expr = getRuntime().newString((ByteList)source.subSequence(memo, p - 1));
			RubyInteger number = RubyNumeric.str2inum(getRuntime(), expr, 10, true);
			return new ParserResult(number, p + 1);
		}
		else {
			return null;
		}
	}

	%%{
		machine JSON_float;
		include JSON_common;

		write data;

		action exit { fbreak; }

		main := '-'?
		        ( ( ( '0' | [1-9][0-9]* ) '.' [0-9]+ ( [Ee] [+\-]?[0-9]+ )? )
		        | ( ( '0' | [1-9][0-9]* ) ( [Ee] [+\-]? [0-9]+ ) ) )
		        ( ^[0-9Ee.\-] @exit );
	}%%

	ParserResult parseFloat(byte[] data, int p, int pe) {
		int cs = EVIL;

		%% write init;
		int memo = p;
		%% write exec;

		if (cs >= JSON_float_first_final) {
			RubyString expr = getRuntime().newString((ByteList)source.subSequence(memo, p - 1));
			RubyFloat number = RubyNumeric.str2fnum(getRuntime(), expr, true);
			return new ParserResult(number, p + 1);
		}
		else {
			return null;
		}
	}

	%%{
		machine JSON_string;
		include JSON_common;

		write data;

		action parse_string {
			result = stringUnescape(memo + 1, p);
			if (result == null) {
				fbreak;
			}
			else {
				fexec p +1;
			}
		}

		action exit { fbreak; }

		main := '"'
		        ( ( ^(["\\]|0..0x1f)
		          | '\\'["\\/bfnrt]
		          | '\\u'[0-9a-fA-F]{4}
		          | '\\'^(["\\/bfnrtu]|0..0x1f)
		          )* %parse_string
		        ) '"' @exit;
	}%%

	ParserResult parseString(byte[] data, int p, int pe) {
		int cs = EVIL;
		RubyString result = null;

		%% write init;
		int memo = p;
		%% write exec;

		if (cs >= JSON_string_first_final && result != null) {
			return new ParserResult(result, p + 1);
		}
		else {
			return null;
		}
	}

	private RubyString stringUnescape(int start, int end) {
		Charset utf8 = null;
		// FIXME: maybe preallocating some room would improve performance?
		RubyString result = getRuntime().newString();

		for (int i = start; i < end; ) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
				if (i >= end) {
					return null;
				}
				c = source.charAt(i);
				switch (c) {
					case '"':
					case '\\':
						result.cat((byte)c);
						i++;
						break;
					case 'b':
						result.cat((byte)'\b');
						i++;
						break;
					case 'f':
						result.cat((byte)'\f');
						i++;
						break;
					case 'n':
						result.cat((byte)'\n');
						i++;
						break;
					case 'r':
						result.cat((byte)'\r');
						i++;
						break;
					case 't':
						result.cat((byte)'\t');
						i++;
						break;
					case 'u':
						// XXX append the UTF-8 representation of characters for now;
						//     once JRuby supports Ruby 1.9, this might change
						i++;
						if (i > end - 4) {
							return null;
						}
						else {
							int code = Integer.parseInt(source.subSequence(i, i + 4).toString(), 16);
							if (code < 128) { // ASCII character
								result.cat((byte)code);
							}
							else {
								if (charset == null) { // lazy-load UTF-8 charset
									charset = Charset.forName("UTF-8");
								}
    							byte[] repr = new String(new char[] {(char)code}).getBytes(utf8);
    							result.cat(repr);
							}
							i += 4;
						}
						break;
					default:
						result.cat((byte)c);
						i++;
				}
			}
			else {
				int j = i;
				while (j < end && source.charAt(j) != '\\') j++;
				result.cat(source.unsafeBytes(), i, j - i);
				i = j;
			}
		}
		return result;
	}

	%%{
		machine JSON_array;
		include JSON_common;

		write data;

		action parse_value {
			ParserResult res = parseValue(data, fpc, pe);
			if (res == null) {
				fbreak;
			}
			else {
				result.append(res.result);
				fexec res.p - 1 /*+1*/;
			}
		}

		action exit { fbreak; }

		next_element = value_separator ignore* begin_value >parse_value;

		main := begin_array
		        ignore*
		        ( ( begin_value >parse_value
		            ignore* )
		          ( ignore*
		            next_element
		            ignore* )* )?
		        ignore*
		        end_array @exit;
	}%%

	ParserResult parseArray(byte[] data, int p, int pe) {
		int cs = EVIL;

		if (maxNesting > 0 && currentNesting > maxNesting) {
			throw new RaiseException(getRuntime(), nestingErrorClass,
				"nesting of " + currentNesting + " is too deep", false);
		}
		RubyArray result = getRuntime().newArray();

		%% write init;
		%% write exec;

		if (cs >= JSON_array_first_final) {
			return new ParserResult(result, p/*+1*/);
		}
		else {
			throw new RaiseException(getRuntime(), parserErrorClass,
				"unexpected token at '" + source.subSequence(p, pe) + "'", false);
		}
	}

	%%{
		machine JSON;
		include JSON_common;

		write data;

		action parse_array {
			this.currentNesting = 1;
			ParserResult res = parseArray(data, fpc, pe);
			if (res == null) {
				fbreak;
			}
			else {
				result = res.result;
				fexec res.p - 1 +1;
			}
		}

		main := ignore*
		        ( begin_array >parse_array )
		        ignore*;
	}%%

>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.rl
	@JRubyMethod(name = "parse")
	public IRubyObject parse() {
		return RubyArray.newArray(getRuntime());
	}

	@JRubyMethod(name = "source")
	public IRubyObject getSource() {
		return vSource;
	}
}
