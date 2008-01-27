// line 1 "src/com/mernen/json/ext/Parser.rl"
package com.mernen.json.ext;

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
import java.util.Arrays;
=======
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
>>>>>>> Reimplemented stringUnescape to use Ruby strings and encode \u escapes as UTF-8:src/com/mernen/json/ext/Parser.java

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
import org.jruby.RubyException;
=======
import org.jruby.RubyFloat;
>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.java
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

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
=======
	private static final int EVIL = 0x666;
	private static final String JSON_MINUS_INFINITY = "-Infinity";

>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.java
	private static final ObjectAllocator PARSER_ALLOCATOR = new ObjectAllocator() {
		public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
			return new Parser(runtime, klazz);
		}
	};

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
=======
	static class ParserResult {
		IRubyObject result;
		int p;

		ParserResult(IRubyObject result, int p) {
			this.result = result;
			this.p = p;
		}
	}

>>>>>>> Added string support:src/com/mernen/json/ext/Parser.java
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

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
=======
=======
>>>>>>> Added string support:src/com/mernen/json/ext/Parser.java
	// line 107 "src/com/mernen/json/ext/Parser.rl"
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
=======
	// line 108 "src/com/mernen/json/ext/Parser.rl"
>>>>>>> Added support for integers:src/com/mernen/json/ext/Parser.java
=======
>>>>>>> Added string support:src/com/mernen/json/ext/Parser.java
=======
	// line 110 "src/com/mernen/json/ext/Parser.rl"
>>>>>>> Reimplemented stringUnescape to use Ruby strings and encode \u escapes as UTF-8:src/com/mernen/json/ext/Parser.java


>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.java
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

<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
=======
	private RaiseException unexpectedToken(int start, int end) {
		return new RaiseException(getRuntime(), parserErrorClass,
			"unexpected token at '" + source.subSequence(start, end) + "'", false);
	}

	
// line 150 "src/com/mernen/json/ext/Parser.java"
private static byte[] init__JSON_value_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1,    1,    2,    1,    3,    1,    4,    1,
	    5,    1,    6,    1,    7,    1,    8
	};
}

private static final byte _JSON_value_actions[] = init__JSON_value_actions_0();


private static byte[] init__JSON_value_key_offsets_0()
{
	return new byte [] {
	    0,    0,   10,   11,   12,   13,   14,   15,   16,   17,   18,   19,
	   20,   21,   22,   23,   24,   25,   26,   27,   28,   29
	};
}

private static final byte _JSON_value_key_offsets[] = init__JSON_value_key_offsets_0();


private static char[] init__JSON_value_trans_keys_0()
{
	return new char [] {
	   34,   45,   73,   78,   91,  102,  110,  116,   48,   57,  110,  102,
	  105,  110,  105,  116,  121,   97,   78,   97,  108,  115,  101,  117,
	  108,  108,  114,  117,  101,    0
	};
}

private static final char _JSON_value_trans_keys[] = init__JSON_value_trans_keys_0();


private static byte[] init__JSON_value_single_lengths_0()
{
	return new byte [] {
	    0,    8,    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
	    1,    1,    1,    1,    1,    1,    1,    1,    1,    0
	};
}

private static final byte _JSON_value_single_lengths[] = init__JSON_value_single_lengths_0();


private static byte[] init__JSON_value_range_lengths_0()
{
	return new byte [] {
	    0,    1,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_value_range_lengths[] = init__JSON_value_range_lengths_0();


private static byte[] init__JSON_value_index_offsets_0()
{
	return new byte [] {
	    0,    0,   10,   12,   14,   16,   18,   20,   22,   24,   26,   28,
	   30,   32,   34,   36,   38,   40,   42,   44,   46,   48
	};
}

private static final byte _JSON_value_index_offsets[] = init__JSON_value_index_offsets_0();


private static byte[] init__JSON_value_trans_targs_wi_0()
{
	return new byte [] {
	   21,   21,    2,    9,   21,   11,   15,   18,   21,    0,    3,    0,
	    4,    0,    5,    0,    6,    0,    7,    0,    8,    0,   21,    0,
	   10,    0,   21,    0,   12,    0,   13,    0,   14,    0,   21,    0,
	   16,    0,   17,    0,   21,    0,   19,    0,   20,    0,   21,    0,
	    0,    0
	};
}

private static final byte _JSON_value_trans_targs_wi[] = init__JSON_value_trans_targs_wi_0();


private static byte[] init__JSON_value_trans_actions_wi_0()
{
	return new byte [] {
	   13,   11,    0,    0,   15,    0,    0,    0,   11,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    9,    0,
	    0,    0,    7,    0,    0,    0,    0,    0,    0,    0,    3,    0,
	    0,    0,    0,    0,    1,    0,    0,    0,    0,    0,    5,    0,
	    0,    0
	};
}

private static final byte _JSON_value_trans_actions_wi[] = init__JSON_value_trans_actions_wi_0();


private static byte[] init__JSON_value_from_state_actions_0()
{
	return new byte [] {
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,   17
	};
}

private static final byte _JSON_value_from_state_actions[] = init__JSON_value_from_state_actions_0();


static final int JSON_value_start = 1;
static final int JSON_value_first_final = 21;
static final int JSON_value_error = 0;

static final int JSON_value_en_main = 1;

// line 261 "src/com/mernen/json/ext/Parser.rl"


	ParserResult parseValue(byte[] data, int p, int pe) {
		int cs = EVIL;
		IRubyObject result = null;

		
// line 271 "src/com/mernen/json/ext/Parser.java"
	{
	cs = JSON_value_start;
	}
// line 268 "src/com/mernen/json/ext/Parser.rl"
		
// line 277 "src/com/mernen/json/ext/Parser.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_acts = _JSON_value_from_state_actions[cs];
	_nacts = (int) _JSON_value_actions[_acts++];
	while ( _nacts-- > 0 ) {
		switch ( _JSON_value_actions[_acts++] ) {
	case 8:
// line 248 "src/com/mernen/json/ext/Parser.rl"
	{
			{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
		}
	break;
// line 308 "src/com/mernen/json/ext/Parser.java"
		}
	}

	_match: do {
	_keys = _JSON_value_key_offsets[cs];
	_trans = _JSON_value_index_offsets[cs];
	_klen = _JSON_value_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_value_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_value_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_value_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_value_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_value_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	cs = _JSON_value_trans_targs_wi[_trans];

	if ( _JSON_value_trans_actions_wi[_trans] != 0 ) {
		_acts = _JSON_value_trans_actions_wi[_trans];
		_nacts = (int) _JSON_value_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_value_actions[_acts++] )
			{
	case 0:
// line 178 "src/com/mernen/json/ext/Parser.rl"
	{
			result = getRuntime().getNil();
		}
	break;
	case 1:
// line 181 "src/com/mernen/json/ext/Parser.rl"
	{
			result = getRuntime().getFalse();
		}
	break;
	case 2:
// line 184 "src/com/mernen/json/ext/Parser.rl"
	{
			result = getRuntime().getTrue();
		}
	break;
	case 3:
// line 187 "src/com/mernen/json/ext/Parser.rl"
	{
			if (allowNaN) {
				result = NAN;
			}
			else {
				throw unexpectedToken(p - 2, pe);
			}
		}
	break;
	case 4:
// line 195 "src/com/mernen/json/ext/Parser.rl"
	{
			if (allowNaN) {
				result = INFINITY;
			}
			else {
				throw unexpectedToken(p - 8, pe);
			}
		}
	break;
	case 5:
// line 203 "src/com/mernen/json/ext/Parser.rl"
	{
			if (pe > p + 9 && source.subSequence(p, p + 9).toString().equals(JSON_MINUS_INFINITY)) {
				if (allowNaN) {
					result = MINUS_INFINITY;
					{p = (( p + 9 /*+1*/))-1;}
					{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
				}
				else {
					throw unexpectedToken(p, pe);
				}
			}
			ParserResult res = parseFloat(data, p, pe);
			if (res != null) {
				result = res.result;
				{p = (( res.p - 1 /*+1*/))-1;}
			}
			res = parseInteger(data, p, pe);
			if (res != null) {
				result = res.result;
				{p = (( res.p - 1 /*+1*/))-1;}
			}
			{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
		}
	break;
	case 6:
// line 226 "src/com/mernen/json/ext/Parser.rl"
	{
			ParserResult res = parseString(data, p, pe);
			if (res == null) {
				{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
			}
			else {
				result = res.result;
				{p = (( res.p - 1 /*+1*/))-1;}
			}
		}
	break;
	case 7:
// line 236 "src/com/mernen/json/ext/Parser.rl"
	{
			currentNesting++;
			ParserResult res = parseArray(data, p, pe);
			currentNesting--;
			if (res == null) {
				{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
			}
			else {
				result = res.result;
				{p = (( res.p))-1;}
			}
		}
	break;
// line 464 "src/com/mernen/json/ext/Parser.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
case 5:
	}
	break; }
	}
// line 269 "src/com/mernen/json/ext/Parser.rl"

		if (cs >= JSON_value_first_final && result != null) {
			return new ParserResult(result, p);
		}
		else {
			return null;
		}
	}

	
// line 494 "src/com/mernen/json/ext/Parser.java"
private static byte[] init__JSON_integer_actions_0()
{
	return new byte [] {
	    0,    1,    0
	};
}

private static final byte _JSON_integer_actions[] = init__JSON_integer_actions_0();


private static byte[] init__JSON_integer_key_offsets_0()
{
	return new byte [] {
	    0,    0,    4,    7,    9,   11
	};
}

private static final byte _JSON_integer_key_offsets[] = init__JSON_integer_key_offsets_0();


private static char[] init__JSON_integer_trans_keys_0()
{
	return new char [] {
	   45,   48,   49,   57,   48,   49,   57,   48,   57,   48,   57,    0
	};
}

private static final char _JSON_integer_trans_keys[] = init__JSON_integer_trans_keys_0();


private static byte[] init__JSON_integer_single_lengths_0()
{
	return new byte [] {
	    0,    2,    1,    0,    0,    0
	};
}

private static final byte _JSON_integer_single_lengths[] = init__JSON_integer_single_lengths_0();


private static byte[] init__JSON_integer_range_lengths_0()
{
	return new byte [] {
	    0,    1,    1,    1,    1,    0
	};
}

private static final byte _JSON_integer_range_lengths[] = init__JSON_integer_range_lengths_0();


private static byte[] init__JSON_integer_index_offsets_0()
{
	return new byte [] {
	    0,    0,    4,    7,    9,   11
	};
}

private static final byte _JSON_integer_index_offsets[] = init__JSON_integer_index_offsets_0();


private static byte[] init__JSON_integer_indicies_0()
{
	return new byte [] {
	    0,    2,    3,    1,    2,    3,    1,    1,    4,    3,    4,    1,
	    0
	};
}

private static final byte _JSON_integer_indicies[] = init__JSON_integer_indicies_0();


private static byte[] init__JSON_integer_trans_targs_wi_0()
{
	return new byte [] {
	    2,    0,    3,    4,    5
	};
}

private static final byte _JSON_integer_trans_targs_wi[] = init__JSON_integer_trans_targs_wi_0();


private static byte[] init__JSON_integer_trans_actions_wi_0()
{
	return new byte [] {
	    0,    0,    0,    0,    1
	};
}

private static final byte _JSON_integer_trans_actions_wi[] = init__JSON_integer_trans_actions_wi_0();


static final int JSON_integer_start = 1;
static final int JSON_integer_first_final = 5;
static final int JSON_integer_error = 0;

static final int JSON_integer_en_main = 1;

// line 286 "src/com/mernen/json/ext/Parser.rl"


	ParserResult parseInteger(byte[] data, int p, int pe) {
		int cs = EVIL;

		
// line 599 "src/com/mernen/json/ext/Parser.java"
	{
	cs = JSON_integer_start;
	}
// line 292 "src/com/mernen/json/ext/Parser.rl"
		int memo = p;
		
// line 606 "src/com/mernen/json/ext/Parser.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_match: do {
	_keys = _JSON_integer_key_offsets[cs];
	_trans = _JSON_integer_index_offsets[cs];
	_klen = _JSON_integer_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_integer_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_integer_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_integer_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_integer_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_integer_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	_trans = _JSON_integer_indicies[_trans];
	cs = _JSON_integer_trans_targs_wi[_trans];

	if ( _JSON_integer_trans_actions_wi[_trans] != 0 ) {
		_acts = _JSON_integer_trans_actions_wi[_trans];
		_nacts = (int) _JSON_integer_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_integer_actions[_acts++] )
			{
	case 0:
// line 283 "src/com/mernen/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 690 "src/com/mernen/json/ext/Parser.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
case 5:
	}
	break; }
	}
// line 294 "src/com/mernen/json/ext/Parser.rl"

		if (cs >= JSON_integer_first_final) {
			RubyString expr = getRuntime().newString((ByteList)source.subSequence(memo, p - 1));
			RubyInteger number = RubyNumeric.str2inum(getRuntime(), expr, 10, true);
			return new ParserResult(number, p + 1);
		}
		else {
			return null;
		}
	}

	
// line 722 "src/com/mernen/json/ext/Parser.java"
private static byte[] init__JSON_float_actions_0()
{
	return new byte [] {
	    0,    1,    0
	};
}

private static final byte _JSON_float_actions[] = init__JSON_float_actions_0();


private static byte[] init__JSON_float_key_offsets_0()
{
	return new byte [] {
	    0,    0,    4,    7,   10,   12,   18,   22,   24,   30,   35
	};
}

private static final byte _JSON_float_key_offsets[] = init__JSON_float_key_offsets_0();


private static char[] init__JSON_float_trans_keys_0()
{
	return new char [] {
	   45,   48,   49,   57,   48,   49,   57,   46,   69,  101,   48,   57,
	   69,  101,   45,   46,   48,   57,   43,   45,   48,   57,   48,   57,
	   69,  101,   45,   46,   48,   57,   46,   69,  101,   48,   57,    0
	};
}

private static final char _JSON_float_trans_keys[] = init__JSON_float_trans_keys_0();


private static byte[] init__JSON_float_single_lengths_0()
{
	return new byte [] {
	    0,    2,    1,    3,    0,    2,    2,    0,    2,    3,    0
	};
}

private static final byte _JSON_float_single_lengths[] = init__JSON_float_single_lengths_0();


private static byte[] init__JSON_float_range_lengths_0()
{
	return new byte [] {
	    0,    1,    1,    0,    1,    2,    1,    1,    2,    1,    0
	};
}

private static final byte _JSON_float_range_lengths[] = init__JSON_float_range_lengths_0();


private static byte[] init__JSON_float_index_offsets_0()
{
	return new byte [] {
	    0,    0,    4,    7,   11,   13,   18,   22,   24,   29,   34
	};
}

private static final byte _JSON_float_index_offsets[] = init__JSON_float_index_offsets_0();


private static byte[] init__JSON_float_indicies_0()
{
	return new byte [] {
	    0,    2,    3,    1,    2,    3,    1,    4,    5,    5,    1,    6,
	    1,    5,    5,    1,    6,    7,    8,    8,    9,    1,    9,    1,
	    1,    1,    1,    9,    7,    4,    5,    5,    3,    1,    1,    0
	};
}

private static final byte _JSON_float_indicies[] = init__JSON_float_indicies_0();


private static byte[] init__JSON_float_trans_targs_wi_0()
{
	return new byte [] {
	    2,    0,    3,    9,    4,    6,    5,   10,    7,    8
	};
}

private static final byte _JSON_float_trans_targs_wi[] = init__JSON_float_trans_targs_wi_0();


private static byte[] init__JSON_float_trans_actions_wi_0()
{
	return new byte [] {
	    0,    0,    0,    0,    0,    0,    0,    1,    0,    0
	};
}

private static final byte _JSON_float_trans_actions_wi[] = init__JSON_float_trans_actions_wi_0();


static final int JSON_float_start = 1;
static final int JSON_float_first_final = 10;
static final int JSON_float_error = 0;

static final int JSON_float_en_main = 1;

// line 317 "src/com/mernen/json/ext/Parser.rl"


	ParserResult parseFloat(byte[] data, int p, int pe) {
		int cs = EVIL;

		
// line 830 "src/com/mernen/json/ext/Parser.java"
	{
	cs = JSON_float_start;
	}
// line 323 "src/com/mernen/json/ext/Parser.rl"
		int memo = p;
		
// line 837 "src/com/mernen/json/ext/Parser.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_match: do {
	_keys = _JSON_float_key_offsets[cs];
	_trans = _JSON_float_index_offsets[cs];
	_klen = _JSON_float_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_float_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_float_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_float_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_float_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_float_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	_trans = _JSON_float_indicies[_trans];
	cs = _JSON_float_trans_targs_wi[_trans];

	if ( _JSON_float_trans_actions_wi[_trans] != 0 ) {
		_acts = _JSON_float_trans_actions_wi[_trans];
		_nacts = (int) _JSON_float_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_float_actions[_acts++] )
			{
	case 0:
// line 311 "src/com/mernen/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 921 "src/com/mernen/json/ext/Parser.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
case 5:
	}
	break; }
	}
// line 325 "src/com/mernen/json/ext/Parser.rl"

		if (cs >= JSON_float_first_final) {
			RubyString expr = getRuntime().newString((ByteList)source.subSequence(memo, p - 1));
			RubyFloat number = RubyNumeric.str2fnum(getRuntime(), expr, true);
			return new ParserResult(number, p + 1);
		}
		else {
			return null;
		}
	}

	
// line 953 "src/com/mernen/json/ext/Parser.java"
private static byte[] init__JSON_string_actions_0()
{
	return new byte [] {
	    0,    2,    0,    1
	};
}

private static final byte _JSON_string_actions[] = init__JSON_string_actions_0();


private static byte[] init__JSON_string_key_offsets_0()
{
	return new byte [] {
	    0,    0,    1,    5,    8,   14,   20,   26,   32
	};
}

private static final byte _JSON_string_key_offsets[] = init__JSON_string_key_offsets_0();


private static char[] init__JSON_string_trans_keys_0()
{
	return new char [] {
	   34,   34,   92,    0,   31,  117,    0,   31,   48,   57,   65,   70,
	   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,   70,
	   97,  102,   48,   57,   65,   70,   97,  102,    0
	};
}

private static final char _JSON_string_trans_keys[] = init__JSON_string_trans_keys_0();


private static byte[] init__JSON_string_single_lengths_0()
{
	return new byte [] {
	    0,    1,    2,    1,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_string_single_lengths[] = init__JSON_string_single_lengths_0();


private static byte[] init__JSON_string_range_lengths_0()
{
	return new byte [] {
	    0,    0,    1,    1,    3,    3,    3,    3,    0
	};
}

private static final byte _JSON_string_range_lengths[] = init__JSON_string_range_lengths_0();


private static byte[] init__JSON_string_index_offsets_0()
{
	return new byte [] {
	    0,    0,    2,    6,    9,   13,   17,   21,   25
	};
}

private static final byte _JSON_string_index_offsets[] = init__JSON_string_index_offsets_0();


private static byte[] init__JSON_string_indicies_0()
{
	return new byte [] {
	    0,    1,    2,    3,    1,    0,    4,    1,    0,    5,    5,    5,
	    1,    6,    6,    6,    1,    7,    7,    7,    1,    0,    0,    0,
	    1,    1,    0
	};
}

private static final byte _JSON_string_indicies[] = init__JSON_string_indicies_0();


private static byte[] init__JSON_string_trans_targs_wi_0()
{
	return new byte [] {
	    2,    0,    8,    3,    4,    5,    6,    7
	};
}

private static final byte _JSON_string_trans_targs_wi[] = init__JSON_string_trans_targs_wi_0();


private static byte[] init__JSON_string_trans_actions_wi_0()
{
	return new byte [] {
	    0,    0,    1,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_string_trans_actions_wi[] = init__JSON_string_trans_actions_wi_0();


static final int JSON_string_start = 1;
static final int JSON_string_first_final = 8;
static final int JSON_string_error = 0;

static final int JSON_string_en_main = 1;

// line 361 "src/com/mernen/json/ext/Parser.rl"


	ParserResult parseString(byte[] data, int p, int pe) {
		int cs = EVIL;
		RubyString result = null;

		
// line 1062 "src/com/mernen/json/ext/Parser.java"
	{
	cs = JSON_string_start;
	}
// line 368 "src/com/mernen/json/ext/Parser.rl"
		int memo = p;
		
// line 1069 "src/com/mernen/json/ext/Parser.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_match: do {
	_keys = _JSON_string_key_offsets[cs];
	_trans = _JSON_string_index_offsets[cs];
	_klen = _JSON_string_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_string_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_string_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_string_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_string_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_string_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	_trans = _JSON_string_indicies[_trans];
	cs = _JSON_string_trans_targs_wi[_trans];

	if ( _JSON_string_trans_actions_wi[_trans] != 0 ) {
		_acts = _JSON_string_trans_actions_wi[_trans];
		_nacts = (int) _JSON_string_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_string_actions[_acts++] )
			{
	case 0:
// line 342 "src/com/mernen/json/ext/Parser.rl"
	{
			result = stringUnescape(memo + 1, p);
			if (result == null) {
				{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
			}
			else {
				{p = (( p +1))-1;}
			}
		}
	break;
	case 1:
// line 352 "src/com/mernen/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 1165 "src/com/mernen/json/ext/Parser.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
case 5:
	}
	break; }
	}
// line 370 "src/com/mernen/json/ext/Parser.rl"

		if (cs >= JSON_string_first_final && result != null) {
			return new ParserResult(result, p + 1);
		}
		else {
			return null;
		}
	}

	private RubyString stringUnescape(int start, int end) {
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
    							byte[] repr;
    							repr = new String(new char[] {(char)code}).getBytes(Charset.forName("UTF-8"));
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

	
// line 1268 "src/com/mernen/json/ext/Parser.java"
private static byte[] init__JSON_array_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1
	};
}

private static final byte _JSON_array_actions[] = init__JSON_array_actions_0();


private static byte[] init__JSON_array_key_offsets_0()
{
	return new byte [] {
	    0,    0,    1,   18,   25,   41,   43,   44,   46,   47,   49,   50,
	   52,   53,   55,   56,   58,   59
	};
}

private static final byte _JSON_array_key_offsets[] = init__JSON_array_key_offsets_0();


private static char[] init__JSON_array_trans_keys_0()
{
	return new char [] {
	   91,   13,   32,   34,   45,   47,   73,   78,   91,   93,  102,  110,
	  116,  123,    9,   10,   48,   57,   13,   32,   44,   47,   93,    9,
	   10,   13,   32,   34,   45,   47,   73,   78,   91,  102,  110,  116,
	  123,    9,   10,   48,   57,   42,   47,   42,   42,   47,   10,   42,
	   47,   42,   42,   47,   10,   42,   47,   42,   42,   47,   10,    0
	};
}

private static final char _JSON_array_trans_keys[] = init__JSON_array_trans_keys_0();


private static byte[] init__JSON_array_single_lengths_0()
{
	return new byte [] {
	    0,    1,   13,    5,   12,    2,    1,    2,    1,    2,    1,    2,
	    1,    2,    1,    2,    1,    0
	};
}

private static final byte _JSON_array_single_lengths[] = init__JSON_array_single_lengths_0();


private static byte[] init__JSON_array_range_lengths_0()
{
	return new byte [] {
	    0,    0,    2,    1,    2,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_array_range_lengths[] = init__JSON_array_range_lengths_0();


private static byte[] init__JSON_array_index_offsets_0()
{
	return new byte [] {
	    0,    0,    2,   18,   25,   40,   43,   45,   48,   50,   53,   55,
	   58,   60,   63,   65,   68,   70
	};
}

private static final byte _JSON_array_index_offsets[] = init__JSON_array_index_offsets_0();


private static byte[] init__JSON_array_indicies_0()
{
	return new byte [] {
	    0,    1,    0,    0,    2,    2,    3,    2,    2,    2,    4,    2,
	    2,    2,    2,    0,    2,    1,    5,    5,    6,    7,    4,    5,
	    1,    6,    6,    2,    2,    8,    2,    2,    2,    2,    2,    2,
	    2,    6,    2,    1,    9,   10,    1,   11,    9,   11,    6,    9,
	    6,   10,   12,   13,    1,   14,   12,   14,    5,   12,    5,   13,
	   15,   16,    1,   17,   15,   17,    0,   15,    0,   16,    1,    0
	};
}

private static final byte _JSON_array_indicies[] = init__JSON_array_indicies_0();


private static byte[] init__JSON_array_trans_targs_wi_0()
{
	return new byte [] {
	    2,    0,    3,   13,   17,    3,    4,    9,    5,    6,    8,    7,
	   10,   12,   11,   14,   16,   15
	};
}

private static final byte _JSON_array_trans_targs_wi[] = init__JSON_array_trans_targs_wi_0();


private static byte[] init__JSON_array_trans_actions_wi_0()
{
	return new byte [] {
	    0,    0,    1,    0,    3,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_array_trans_actions_wi[] = init__JSON_array_trans_actions_wi_0();


static final int JSON_array_start = 1;
static final int JSON_array_first_final = 17;
static final int JSON_array_error = 0;

static final int JSON_array_en_main = 1;

// line 482 "src/com/mernen/json/ext/Parser.rl"


	ParserResult parseArray(byte[] data, int p, int pe) {
		int cs = EVIL;

		if (maxNesting > 0 && currentNesting > maxNesting) {
			throw new RaiseException(getRuntime(), nestingErrorClass,
				"nesting of " + currentNesting + " is too deep", false);
		}
		RubyArray result = getRuntime().newArray();

		
// line 1393 "src/com/mernen/json/ext/Parser.java"
	{
	cs = JSON_array_start;
	}
// line 494 "src/com/mernen/json/ext/Parser.rl"
		
// line 1399 "src/com/mernen/json/ext/Parser.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_match: do {
	_keys = _JSON_array_key_offsets[cs];
	_trans = _JSON_array_index_offsets[cs];
	_klen = _JSON_array_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_array_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_array_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_array_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_array_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_array_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	_trans = _JSON_array_indicies[_trans];
	cs = _JSON_array_trans_targs_wi[_trans];

	if ( _JSON_array_trans_actions_wi[_trans] != 0 ) {
		_acts = _JSON_array_trans_actions_wi[_trans];
		_nacts = (int) _JSON_array_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_array_actions[_acts++] )
			{
	case 0:
// line 458 "src/com/mernen/json/ext/Parser.rl"
	{
			ParserResult res = parseValue(data, p, pe);
			if (res == null) {
				{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
			}
			else {
				result.append(res.result);
				{p = (( res.p - 1 /*+1*/))-1;}
			}
		}
	break;
	case 1:
// line 469 "src/com/mernen/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 1496 "src/com/mernen/json/ext/Parser.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
case 5:
	}
	break; }
	}
// line 495 "src/com/mernen/json/ext/Parser.rl"

		if (cs >= JSON_array_first_final) {
			return new ParserResult(result, p/*+1*/);
		}
		else {
			throw new RaiseException(getRuntime(), parserErrorClass,
				"unexpected token at '" + source.subSequence(p, pe) + "'", false);
		}
	}

	
// line 1527 "src/com/mernen/json/ext/Parser.java"
private static byte[] init__JSON_actions_0()
{
	return new byte [] {
	    0,    1,    0
	};
}

private static final byte _JSON_actions[] = init__JSON_actions_0();


private static byte[] init__JSON_key_offsets_0()
{
	return new byte [] {
	    0,    0,    6,    8,    9,   11,   12,   14,   15,   17,   18
	};
}

private static final byte _JSON_key_offsets[] = init__JSON_key_offsets_0();


private static char[] init__JSON_trans_keys_0()
{
	return new char [] {
	   13,   32,   47,   91,    9,   10,   42,   47,   42,   42,   47,   10,
	   42,   47,   42,   42,   47,   10,   13,   32,   47,    9,   10,    0
	};
}

private static final char _JSON_trans_keys[] = init__JSON_trans_keys_0();


private static byte[] init__JSON_single_lengths_0()
{
	return new byte [] {
	    0,    4,    2,    1,    2,    1,    2,    1,    2,    1,    3
	};
}

private static final byte _JSON_single_lengths[] = init__JSON_single_lengths_0();


private static byte[] init__JSON_range_lengths_0()
{
	return new byte [] {
	    0,    1,    0,    0,    0,    0,    0,    0,    0,    0,    1
	};
}

private static final byte _JSON_range_lengths[] = init__JSON_range_lengths_0();


private static byte[] init__JSON_index_offsets_0()
{
	return new byte [] {
	    0,    0,    6,    9,   11,   14,   16,   19,   21,   24,   26
	};
}

private static final byte _JSON_index_offsets[] = init__JSON_index_offsets_0();


private static byte[] init__JSON_indicies_0()
{
	return new byte [] {
	    0,    0,    2,    3,    0,    1,    4,    5,    1,    6,    4,    6,
	    0,    4,    0,    5,    7,    8,    1,    9,    7,    9,   10,    7,
	   10,    8,   10,   10,   11,   10,    1,    0
	};
}

private static final byte _JSON_indicies[] = init__JSON_indicies_0();


private static byte[] init__JSON_trans_targs_wi_0()
{
	return new byte [] {
	    1,    0,    2,   10,    3,    5,    4,    7,    9,    8,   10,    6
	};
}

private static final byte _JSON_trans_targs_wi[] = init__JSON_trans_targs_wi_0();


private static byte[] init__JSON_trans_actions_wi_0()
{
	return new byte [] {
	    0,    0,    0,    1,    0,    0,    0,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_trans_actions_wi[] = init__JSON_trans_actions_wi_0();


static final int JSON_start = 1;
static final int JSON_first_final = 10;
static final int JSON_error = 0;

static final int JSON_en_main = 1;

// line 526 "src/com/mernen/json/ext/Parser.rl"


>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.java
	@JRubyMethod(name = "parse")
	public IRubyObject parse() {
<<<<<<< HEAD:src/com/mernen/json/ext/Parser.java
		return RubyArray.newArray(getRuntime());
=======
		int cs = EVIL;
		int p, pe;
		IRubyObject result = getRuntime().getNil();
		byte[] data = source.bytes();

		
// line 1638 "src/com/mernen/json/ext/Parser.java"
	{
	cs = JSON_start;
	}
// line 536 "src/com/mernen/json/ext/Parser.rl"
		p = 0;
		pe = len;
		
// line 1646 "src/com/mernen/json/ext/Parser.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_match: do {
	_keys = _JSON_key_offsets[cs];
	_trans = _JSON_index_offsets[cs];
	_klen = _JSON_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	_trans = _JSON_indicies[_trans];
	cs = _JSON_trans_targs_wi[_trans];

	if ( _JSON_trans_actions_wi[_trans] != 0 ) {
		_acts = _JSON_trans_actions_wi[_trans];
		_nacts = (int) _JSON_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_actions[_acts++] )
			{
	case 0:
// line 511 "src/com/mernen/json/ext/Parser.rl"
	{
			this.currentNesting = 1;
			ParserResult res = parseArray(data, p, pe);
			if (res == null) {
				{ p += 1; _goto_targ = 5; if (true)  continue _goto;}
			}
			else {
				result = res.result;
				{p = (( res.p - 1 +1))-1;}
			}
		}
	break;
// line 1740 "src/com/mernen/json/ext/Parser.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
case 5:
	}
	break; }
	}
// line 539 "src/com/mernen/json/ext/Parser.rl"

		if (cs >= JSON_first_final && p == pe) {
			return result;
		}
		else {
			throw new RaiseException(getRuntime(), parserErrorClass,
				"unexpected token at '" + source.subSequence(p, pe) + "'", false);
		}
>>>>>>> Added support for floating-point numbers:src/com/mernen/json/ext/Parser.java
	}

	@JRubyMethod(name = "source")
	public IRubyObject getSource() {
		return vSource;
	}
}
