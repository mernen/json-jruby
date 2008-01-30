/*
 * This code is copyrighted work by Daniel Luz <@gmail.com: mernen>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * The <code>JSON::Ext::Parser</code> class.
 * 
 * <p>This is the JSON parser implemented as a Java class. To use it as the
 * standard parser, set
 *   <pre>JSON.parser = JSON::Ext::Parser</pre>
 * This is performed for you when you <code>include "json/ext"</code>.
 * 
 * @author mernen
 */
public class Parser extends RubyObject {
	private static final long serialVersionUID = 2782556972195914229L;

	private RubyString vSource;
	private ByteList source;
	private int len;
	private RubyString createId;
	private int maxNesting;
	private int currentNesting;
	private boolean allowNaN;

	private static final int EVIL = 0x666;
	private static final String JSON_MINUS_INFINITY = "-Infinity";
	// constant names in the JSON module containing those values
	private static final String CONST_NAN = "NaN";
	private static final String CONST_INFINITY = "Infinity";
	private static final String CONST_MINUS_INFINITY = "MinusInfinity";

	static final ObjectAllocator ALLOCATOR = new ObjectAllocator() {
		public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
			return new Parser(runtime, klazz);
		}
	};

	/**
	 * Multiple-value return for internal parser methods.
	 * 
	 * <p>All the <code>parse<var>Stuff</var></code> methods return instances of
	 * <code>ParserResult</code> when successful, or <code>null</code> when
	 * there's a problem with the input data.
	 */
	static class ParserResult {
		/**
		 * The result of the successful parsing. Should never be
		 * <code>null</code>.
		 */
		final IRubyObject result;
		/**
		 * The point where the parser returned.
		 */
		final int p;

		ParserResult(IRubyObject result, int p) {
			this.result = result;
			this.p = p;
		}
	}

	public Parser(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	%%{
		machine JSON_common;

		cr                  = '\n';
		cr_neg              = [^\n];
		ws                  = [ \t\r\n];
		c_comment           = '/*' ( any* - (any* '*/' any* ) ) '*/';
		cpp_comment         = '//' cr_neg* cr;
		comment             = c_comment | cpp_comment;
		ignore              = ws | comment;
		name_separator      = ':';
		value_separator     = ',';
		Vnull               = 'null';
		Vfalse              = 'false';
		Vtrue               = 'true';
		VNaN                = 'NaN';
		VInfinity           = 'Infinity';
		VMinusInfinity      = '-Infinity';
		begin_value         = [nft"\-[{NI] | digit;
		begin_object        = '{';
		end_object          = '}';
		begin_array         = '[';
		end_array           = ']';
		begin_string        = '"';
		begin_name          = begin_string;
		begin_number        = digit | '-';
	}%%

	/**
	 * <code>Parser.new(source, opts = {})</code>
	 * 
	 * <p>Creates a new <code>JSON::Ext::Parser</code> instance for the string
	 * <code>source</code>.
	 * It will be configured by the <code>opts</code> Hash.
	 * <code>opts</code> can have the following keys:
	 * 
	 * <dl>
	 * <dt><code>:max_nesting</code>
	 * <dd>The maximum depth of nesting allowed in the parsed data
	 * structures. Disable depth checking with <code>:max_nesting => false|nil|0</code>,
	 * it defaults to 19.
	 * 
	 * <dt><code>:allow_nan</code>
	 * <dd>If set to <code>true</code>, allow <code>NaN</code>,
	 * <code>Infinity</code> and <code>-Infinity</code> in defiance of RFC 4627
	 * to be parsed by the Parser. This option defaults to <code>false</code>.
	 * 
	 * <dt><code>:create_additions</code>
	 * <dd>If set to <code>false</code>, the Parser doesn't create additions
	 * even if a matchin class and <code>create_id</code> was found. This option
	 * defaults to <code>true</code>.
	 * </dl>
	 */
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
		ByteList sourceBytes = source.getByteList();
		int len = sourceBytes.length();

		if (len < 2) {
			throw Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
				"A JSON text must at least contain two octets!");
		}

		if (args.length > 1) {
			RubyHash opts = args[1].convertToHash();

			IRubyObject maxNesting = Utils.fastGetSymItem(opts, "max_nesting");
			if (maxNesting == null) {
				this.maxNesting = 19;
			}
			else if (!maxNesting.isTrue()) {
				this.maxNesting = 0;
			}
			else {
				this.maxNesting = RubyNumeric.fix2int(maxNesting);
			}

			IRubyObject allowNaN = Utils.fastGetSymItem(opts, "allow_nan");
			this.allowNaN = allowNaN != null && allowNaN.isTrue();

			IRubyObject createAdditions = Utils.fastGetSymItem(opts, "create_additions");
			if (createAdditions == null || createAdditions.isTrue()) {
				this.createId = getCreateId();
			}
			else {
				this.createId = null;
			}
		}
		else {
			this.maxNesting = 19;
			this.allowNaN = false;
			this.createId = getCreateId();
		}

		this.currentNesting = 0;

		this.len = len;
		this.source = sourceBytes;
		this.vSource = source;

		return this;
	}

	/**
	 * Queries <code>JSON.create_id</code>. Returns <code>null</code> if it is
	 * set to <code>nil</code> or <code>false</code>, and a String if not.
	 */
	private RubyString getCreateId() {
		Ruby runtime = getRuntime();
		IRubyObject v = runtime.getModule("JSON").
			callMethod(runtime.getCurrentContext(), "create_id");
		return v.isTrue() ? v.convertToString() : null;
	}

	private RaiseException unexpectedToken(int start, int end) {
		return Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
			"unexpected token at '" + source.subSequence(start, end) + "'");
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
				result = getConstant(CONST_NAN);
			}
			else {
				throw unexpectedToken(p - 2, pe);
			}
		}
		action parse_infinity {
			if (allowNaN) {
				result = getConstant(CONST_INFINITY);
			}
			else {
				throw unexpectedToken(p - 7, pe);
			}
		}
		action parse_number {
			if (pe > fpc + 9 &&
			    source.subSequence(fpc, fpc + 9).toString().equals(JSON_MINUS_INFINITY)) {

				if (allowNaN) {
					result = getConstant(CONST_MINUS_INFINITY);
					fexec p + 10;
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
		action parse_object {
			currentNesting++;
			ParserResult res = parseObject(data, fpc, pe);
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
		          begin_array >parse_array |
		          begin_object >parse_object
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
		RubyString result = getRuntime().newString();
		// XXX maybe other values would be better for preallocation?
		result.modify(end - start);

		int surrogateStart = -1;
		char surrogate = 0;

		for (int i = start; i < end; ) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
				if (i >= end) {
					return null;
				}
				c = source.charAt(i);
				if (surrogateStart != -1 && c != 'u') {
					throw Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
						"partial character in source, but hit end near " +
						source.subSequence(surrogateStart, end));
				}
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
							String digits = source.subSequence(i, i + 4).toString();
							int code = Integer.parseInt(digits, 16);
							if (surrogateStart != -1) {
								if (Character.isLowSurrogate((char)code)) {
									int fullCode = Character.toCodePoint(surrogate, (char)code);
									result.cat(getUTF8Bytes(fullCode | 0L));
									surrogateStart = -1;
									surrogate = 0;
								}
								else {
									throw Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
										"partial character in source, but hit end near " +
										source.subSequence(surrogateStart, end));
								}
							}
							else if (Character.isHighSurrogate((char)code)) {
								surrogateStart = i - 2;
								surrogate = (char)code;
							}
							else {
    							result.cat(getUTF8Bytes(code));
							}
							i += 4;
						}
						break;
					default:
						result.cat((byte)c);
						i++;
				}
			}
			else if (surrogateStart != -1) {
				throw Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
					"partial character in source, but hit end near " +
					source.subSequence(surrogateStart, end));
			}
			else {
				int j = i;
				while (j < end && source.charAt(j) != '\\') j++;
				result.cat(source.unsafeBytes(), i, j - i);
				i = j;
			}
		}
		if (surrogateStart != -1) {
			throw Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
				"partial character in source, but hit end near " +
				source.subSequence(surrogateStart, end));
		}
		return result;
	}

	/**
	 * Converts a code point into an UTF-8 representation.
	 * @param code The character code point
	 * @return An array containing the UTF-8 bytes for the given code point
	 */
	private static byte[] getUTF8Bytes(long code) {
		if (code < 0x80) {
			return new byte[] {(byte)code};
		}
		if (code < 0x800) {
			return new byte[] {(byte)(0xc0 | code >>> 6),
			                   (byte)(0x80 | code & 0x3f)};
		}
		if (code < 0x10000) {
			return new byte[] {(byte)(0xe0 | code >>> 12),
			                   (byte)(0x80 | code >>> 6 & 0x3f),
			                   (byte)(0x80 | code & 0x3f)};
		}
		return new byte[] {(byte)(0xf0 | code >>> 18),
		                   (byte)(0x80 | code >>> 12 & 0x3f),
		                   (byte)(0x80 | code >>> 6 & 0x3f),
		                   (byte)(0x80 | code & 0x3f)};
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
			throw Utils.newException(getRuntime(), Utils.M_NESTING_ERROR,
				"nesting of " + currentNesting + " is too deep");
		}

		RubyArray result = getRuntime().newArray();

		%% write init;
		%% write exec;

		if (cs >= JSON_array_first_final) {
			return new ParserResult(result, p/*+1*/);
		}
		else {
			throw unexpectedToken(p, pe);
		}
	}

	%%{
		machine JSON_object;
		include JSON_common;

		write data;

		action parse_value {
			ParserResult res = parseValue(data, fpc, pe);
			if (res == null) {
				fbreak;
			}
			else {
				result.op_aset(lastName, res.result);
				fexec res.p - 1 /*+1*/;
			}
		}

		action parse_name {
			ParserResult res = parseString(data, fpc, pe);
			if (res == null) {
				fbreak;
			}
			else {
				lastName = res.result;
				fexec res.p - 1 /*+1*/;
			}
		}

		action exit { fbreak; }

		a_pair = ignore*
		         begin_name >parse_name
		         ignore* name_separator ignore*
		         begin_value >parse_value;

		main := begin_object
		        (a_pair (ignore* value_separator a_pair)*)?
		        ignore* end_object @exit;
	}%%

	ParserResult parseObject(byte[] data, int p, int pe) {
		int cs = EVIL;
		IRubyObject lastName = null;
		Ruby runtime = getRuntime();

		if (maxNesting > 0 && currentNesting > maxNesting) {
			throw Utils.newException(runtime, Utils.M_NESTING_ERROR,
				"nesting of " + currentNesting + " is too deep");
		}

		RubyHash result = RubyHash.newHash(runtime);

		%% write init;
		%% write exec;

		if (cs >= JSON_object_first_final) {
			IRubyObject returnedResult = result;

			// attempt to de-serialize object
			if (createId != null) {
				IRubyObject vKlassName = result.op_aref(createId);
				if (!vKlassName.isNil()) {
					String klassName = vKlassName.asJavaString();
					RubyModule klass;
					try {
						klass = runtime.getClassFromPath(klassName);
					}
					catch (RaiseException e) {
						if (runtime.getClass("NameError").isInstance(e.getException())) {
							// invalid class path, but we're supposed to return ArgumentError
							throw runtime.newArgumentError("undefined class/module " +
							                               klassName);
						}
						else {
							// some other exception; let it propagate
							throw e;
						}
					}
					ThreadContext context = runtime.getCurrentContext();
					if (klass.respondsTo("json_creatable?") &&
					    klass.callMethod(context, "json_creatable?").isTrue()) {

						returnedResult = klass.callMethod(context, "json_create", result);
					}
				}
			}
			return new ParserResult(returnedResult, p /*+1*/);
		}
		else {
			return null;
		}
	}

	%%{
		machine JSON;
		include JSON_common;

		write data;

		action parse_object {
			currentNesting = 1;
			ParserResult res = parseObject(data, fpc, pe);
			if (res == null) {
				fbreak;
			}
			else {
				result = res.result;
				fexec res.p - 1 +1;
			}
		}

		action parse_array {
			currentNesting = 1;
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
		        ( begin_object >parse_object
		        | begin_array >parse_array )
		        ignore*;
	}%%

	/**
	 * <code>Parser#parse()</code>
	 * 
	 * <p>Parses the current JSON text <code>source</code> and returns the
	 * complete data structure as a result.
	 */
	@JRubyMethod(name = "parse")
	public IRubyObject parse() {
		int cs = EVIL;
		int p, pe;
		IRubyObject result = getRuntime().getNil();
		byte[] data = source.bytes();

		%% write init;
		p = 0;
		pe = len;
		%% write exec;

		if (cs >= JSON_first_final && p == pe) {
			return result;
		}
		else {
			throw unexpectedToken(p, pe);
		}
	}

	/**
	 * <code>Parser#source()</code>
	 * 
	 * <p>Returns a copy of the current <code>source</code> string, that was
	 * used to construct this Parser.
	 */
	@JRubyMethod(name = "source")
	public IRubyObject source_get() {
		return vSource.dup();
	}

	/**
	 * Retrieves a constant directly descended from the <code>JSON</code> module.
	 * @param name The constant name
	 */
	private IRubyObject getConstant(String name) {
		return getRuntime().getModule("JSON").getConstant(name);
	}
}
