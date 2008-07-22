// line 1 "src/json/ext/Parser.rl"
/*
 * This code is copyrighted work by Daniel Luz <dev at mernen dot com>.
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
 * <p>This class does not perform the actual parsing, just acts as an interface
 * to Ruby code. When the {@link #parse()} method is invoked, a
 * Parser.ParserSession object is instantiated, which handles the process.
 * 
 * @author mernen
 */
public class Parser extends RubyObject {
    private RubyString vSource;
    private RubyString createId;
    private int maxNesting;
    private boolean allowNaN;

    private static final int DEFAULT_MAX_NESTING = 19;

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
    static final class ParserResult {
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
        int len = source.getByteList().length();

        if (len < 2) {
            throw Utils.newException(getRuntime(), Utils.M_PARSER_ERROR,
                "A JSON text must at least contain two octets!");
        }

        if (args.length > 1) {
            RubyHash opts = args[1].convertToHash();

            IRubyObject maxNesting = Utils.fastGetSymItem(opts, "max_nesting");
            if (maxNesting == null) {
                this.maxNesting = DEFAULT_MAX_NESTING;
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
            this.maxNesting = DEFAULT_MAX_NESTING;
            this.allowNaN = false;
            this.createId = getCreateId();
        }

        this.vSource = source;
        return this;
    }

    /**
     * <code>Parser#parse()</code>
     * 
     * <p>Parses the current JSON text <code>source</code> and returns the
     * complete data structure as a result.
     */
    @JRubyMethod(name = "parse")
    public IRubyObject parse() {
        return new ParserSession(this).parse();
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
     * Queries <code>JSON.create_id</code>. Returns <code>null</code> if it is
     * set to <code>nil</code> or <code>false</code>, and a String if not.
     */
    private RubyString getCreateId() {
        IRubyObject v = getRuntime().getModule("JSON").
            callMethod(getRuntime().getCurrentContext(), "create_id");
        return v.isTrue() ? v.convertToString() : null;
    }

    /**
     * A string parsing session.
     * 
     * <p>Once a ParserSession is instantiated, the source string should not
     * change until the parsing is complete. The ParserSession object assumes
     * the source {@link RubyString} is still associated to its original
     * {@link ByteList}, which in turn must still be bound to the same
     * <code>byte[]</code> value (and on the same offset).
     */
    private static class ParserSession {
        private final Parser parser;
        private final Ruby runtime;
        private final ByteList byteList;
        private final byte[] data;
        private int currentNesting = 0;

        // initialization value for all state variables.
        // no idea about the origins of this value, ask Flori ;)
        private static final int EVIL = 0x666;

        private ParserSession(Parser parser) {
            this.parser = parser;
            runtime = parser.getRuntime();
            byteList = parser.vSource.getByteList();
            data = byteList.unsafeBytes();
        }

        private RaiseException unexpectedToken(int start, int end) {
            RubyString msg =
                runtime.newString("unexpected token at '")
                       .cat(data, byteList.begin() + start, end - start)
                       .cat((byte)'\'');
            return Utils.newException(runtime, Utils.M_PARSER_ERROR, msg);
        }

        // line 263 "src/json/ext/Parser.rl"


        
// line 243 "src/json/ext/Parser.java"
private static byte[] init__JSON_value_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1,    1,    2,    1,    3,    1,    4,    1,
	    5,    1,    6,    1,    7,    1,    8,    1,    9
	};
}

private static final byte _JSON_value_actions[] = init__JSON_value_actions_0();


private static byte[] init__JSON_value_key_offsets_0()
{
	return new byte [] {
	    0,    0,   11,   12,   13,   14,   15,   16,   17,   18,   19,   20,
	   21,   22,   23,   24,   25,   26,   27,   28,   29,   30
	};
}

private static final byte _JSON_value_key_offsets[] = init__JSON_value_key_offsets_0();


private static char[] init__JSON_value_trans_keys_0()
{
	return new char [] {
	   34,   45,   73,   78,   91,  102,  110,  116,  123,   48,   57,  110,
	  102,  105,  110,  105,  116,  121,   97,   78,   97,  108,  115,  101,
	  117,  108,  108,  114,  117,  101,    0
	};
}

private static final char _JSON_value_trans_keys[] = init__JSON_value_trans_keys_0();


private static byte[] init__JSON_value_single_lengths_0()
{
	return new byte [] {
	    0,    9,    1,    1,    1,    1,    1,    1,    1,    1,    1,    1,
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
	    0,    0,   11,   13,   15,   17,   19,   21,   23,   25,   27,   29,
	   31,   33,   35,   37,   39,   41,   43,   45,   47,   49
	};
}

private static final byte _JSON_value_index_offsets[] = init__JSON_value_index_offsets_0();


private static byte[] init__JSON_value_trans_targs_0()
{
	return new byte [] {
	   21,   21,    2,    9,   21,   11,   15,   18,   21,   21,    0,    3,
	    0,    4,    0,    5,    0,    6,    0,    7,    0,    8,    0,   21,
	    0,   10,    0,   21,    0,   12,    0,   13,    0,   14,    0,   21,
	    0,   16,    0,   17,    0,   21,    0,   19,    0,   20,    0,   21,
	    0,    0,    0
	};
}

private static final byte _JSON_value_trans_targs[] = init__JSON_value_trans_targs_0();


private static byte[] init__JSON_value_trans_actions_0()
{
	return new byte [] {
	   13,   11,    0,    0,   15,    0,    0,    0,   17,   11,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    9,
	    0,    0,    0,    7,    0,    0,    0,    0,    0,    0,    0,    3,
	    0,    0,    0,    0,    0,    1,    0,    0,    0,    0,    0,    5,
	    0,    0,    0
	};
}

private static final byte _JSON_value_trans_actions[] = init__JSON_value_trans_actions_0();


private static byte[] init__JSON_value_from_state_actions_0()
{
	return new byte [] {
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,   19
	};
}

private static final byte _JSON_value_from_state_actions[] = init__JSON_value_from_state_actions_0();


static final int JSON_value_start = 1;
static final int JSON_value_first_final = 21;
static final int JSON_value_error = 0;

static final int JSON_value_en_main = 1;

// line 369 "src/json/ext/Parser.rl"


        ParserResult parseValue(int p, int pe) {
            int cs = EVIL;
            IRubyObject result = null;

            
// line 364 "src/json/ext/Parser.java"
	{
	cs = JSON_value_start;
	}
// line 376 "src/json/ext/Parser.rl"
            
// line 370 "src/json/ext/Parser.java"
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
	case 9:
// line 355 "src/json/ext/Parser.rl"
	{
                { p += 1; _goto_targ = 5; if (true)  continue _goto;}
            }
	break;
// line 401 "src/json/ext/Parser.java"
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

	cs = _JSON_value_trans_targs[_trans];

	if ( _JSON_value_trans_actions[_trans] != 0 ) {
		_acts = _JSON_value_trans_actions[_trans];
		_nacts = (int) _JSON_value_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_value_actions[_acts++] )
			{
	case 0:
// line 271 "src/json/ext/Parser.rl"
	{
                result = runtime.getNil();
            }
	break;
	case 1:
// line 274 "src/json/ext/Parser.rl"
	{
                result = runtime.getFalse();
            }
	break;
	case 2:
// line 277 "src/json/ext/Parser.rl"
	{
                result = runtime.getTrue();
            }
	break;
	case 3:
// line 280 "src/json/ext/Parser.rl"
	{
                if (parser.allowNaN) {
                    result = getConstant(CONST_NAN);
                }
                else {
                    throw unexpectedToken(p - 2, pe);
                }
            }
	break;
	case 4:
// line 288 "src/json/ext/Parser.rl"
	{
                if (parser.allowNaN) {
                    result = getConstant(CONST_INFINITY);
                }
                else {
                    throw unexpectedToken(p - 7, pe);
                }
            }
	break;
	case 5:
// line 296 "src/json/ext/Parser.rl"
	{
                if (pe > p + 9 &&
                    byteList.subSequence(p, p + 9).toString().equals(JSON_MINUS_INFINITY)) {

                    if (parser.allowNaN) {
                        result = getConstant(CONST_MINUS_INFINITY);
                        {p = (( p + 10))-1;}
                        { p += 1; _goto_targ = 5; if (true)  continue _goto;}
                    }
                    else {
                        throw unexpectedToken(p, pe);
                    }
                }
                ParserResult res = parseFloat(p, pe);
                if (res != null) {
                    result = res.result;
                    {p = (( res.p - 1 /*+1*/))-1;}
                }
                res = parseInteger(p, pe);
                if (res != null) {
                    result = res.result;
                    {p = (( res.p - 1 /*+1*/))-1;}
                }
                { p += 1; _goto_targ = 5; if (true)  continue _goto;}
            }
	break;
	case 6:
// line 321 "src/json/ext/Parser.rl"
	{
                ParserResult res = parseString(p, pe);
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
// line 331 "src/json/ext/Parser.rl"
	{
                currentNesting++;
                ParserResult res = parseArray(p, pe);
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
	case 8:
// line 343 "src/json/ext/Parser.rl"
	{
                currentNesting++;
                ParserResult res = parseObject(p, pe);
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
// line 574 "src/json/ext/Parser.java"
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
// line 377 "src/json/ext/Parser.rl"

            if (cs >= JSON_value_first_final && result != null) {
                return new ParserResult(result, p);
            }
            else {
                return null;
            }
        }

        
// line 604 "src/json/ext/Parser.java"
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


private static byte[] init__JSON_integer_trans_targs_0()
{
	return new byte [] {
	    2,    0,    3,    4,    5
	};
}

private static final byte _JSON_integer_trans_targs[] = init__JSON_integer_trans_targs_0();


private static byte[] init__JSON_integer_trans_actions_0()
{
	return new byte [] {
	    0,    0,    0,    0,    1
	};
}

private static final byte _JSON_integer_trans_actions[] = init__JSON_integer_trans_actions_0();


static final int JSON_integer_start = 1;
static final int JSON_integer_first_final = 5;
static final int JSON_integer_error = 0;

static final int JSON_integer_en_main = 1;

// line 394 "src/json/ext/Parser.rl"


        ParserResult parseInteger(int p, int pe) {
            int cs = EVIL;

            
// line 709 "src/json/ext/Parser.java"
	{
	cs = JSON_integer_start;
	}
// line 400 "src/json/ext/Parser.rl"
            int memo = p;
            
// line 716 "src/json/ext/Parser.java"
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
	cs = _JSON_integer_trans_targs[_trans];

	if ( _JSON_integer_trans_actions[_trans] != 0 ) {
		_acts = _JSON_integer_trans_actions[_trans];
		_nacts = (int) _JSON_integer_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_integer_actions[_acts++] )
			{
	case 0:
// line 391 "src/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 800 "src/json/ext/Parser.java"
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
// line 402 "src/json/ext/Parser.rl"

            if (cs < JSON_integer_first_final) {
                return null;
            }

            ByteList num = (ByteList)byteList.subSequence(memo, p - 1);
            // note: this is actually a shared string, but since it is temporary and
            //       read-only, it doesn't really matter
            RubyString expr = RubyString.newStringLight(runtime, num);
            RubyInteger number = RubyNumeric.str2inum(runtime, expr, 10, true);
            return new ParserResult(number, p + 1);
        }

        
// line 834 "src/json/ext/Parser.java"
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


private static byte[] init__JSON_float_trans_targs_0()
{
	return new byte [] {
	    2,    0,    3,    9,    4,    6,    5,   10,    7,    8
	};
}

private static final byte _JSON_float_trans_targs[] = init__JSON_float_trans_targs_0();


private static byte[] init__JSON_float_trans_actions_0()
{
	return new byte [] {
	    0,    0,    0,    0,    0,    0,    0,    1,    0,    0
	};
}

private static final byte _JSON_float_trans_actions[] = init__JSON_float_trans_actions_0();


static final int JSON_float_start = 1;
static final int JSON_float_first_final = 10;
static final int JSON_float_error = 0;

static final int JSON_float_en_main = 1;

// line 427 "src/json/ext/Parser.rl"


        ParserResult parseFloat(int p, int pe) {
            int cs = EVIL;

            
// line 942 "src/json/ext/Parser.java"
	{
	cs = JSON_float_start;
	}
// line 433 "src/json/ext/Parser.rl"
            int memo = p;
            
// line 949 "src/json/ext/Parser.java"
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
	cs = _JSON_float_trans_targs[_trans];

	if ( _JSON_float_trans_actions[_trans] != 0 ) {
		_acts = _JSON_float_trans_actions[_trans];
		_nacts = (int) _JSON_float_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_float_actions[_acts++] )
			{
	case 0:
// line 421 "src/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 1033 "src/json/ext/Parser.java"
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
// line 435 "src/json/ext/Parser.rl"

            if (cs < JSON_float_first_final) {
                return null;
            }

            ByteList num = (ByteList)byteList.subSequence(memo, p - 1);
            // note: this is actually a shared string, but since it is temporary and
            //       read-only, it doesn't really matter
            RubyString expr = RubyString.newStringLight(runtime, num);
            RubyFloat number = RubyNumeric.str2fnum(runtime, expr, true);
            return new ParserResult(number, p + 1);
        }

        
// line 1067 "src/json/ext/Parser.java"
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


private static byte[] init__JSON_string_trans_targs_0()
{
	return new byte [] {
	    2,    0,    8,    3,    4,    5,    6,    7
	};
}

private static final byte _JSON_string_trans_targs[] = init__JSON_string_trans_targs_0();


private static byte[] init__JSON_string_trans_actions_0()
{
	return new byte [] {
	    0,    0,    1,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_string_trans_actions[] = init__JSON_string_trans_actions_0();


static final int JSON_string_start = 1;
static final int JSON_string_first_final = 8;
static final int JSON_string_error = 0;

static final int JSON_string_en_main = 1;

// line 473 "src/json/ext/Parser.rl"


        ParserResult parseString(int p, int pe) {
            int cs = EVIL;
            RubyString result = null;

            
// line 1176 "src/json/ext/Parser.java"
	{
	cs = JSON_string_start;
	}
// line 480 "src/json/ext/Parser.rl"
            int memo = p;
            
// line 1183 "src/json/ext/Parser.java"
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
	cs = _JSON_string_trans_targs[_trans];

	if ( _JSON_string_trans_actions[_trans] != 0 ) {
		_acts = _JSON_string_trans_actions[_trans];
		_nacts = (int) _JSON_string_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_string_actions[_acts++] )
			{
	case 0:
// line 454 "src/json/ext/Parser.rl"
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
// line 464 "src/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 1279 "src/json/ext/Parser.java"
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
// line 482 "src/json/ext/Parser.rl"

            if (cs >= JSON_string_first_final && result != null) {
                return new ParserResult(result, p + 1);
            }
            else {
                return null;
            }
        }

        private RubyString stringUnescape(int start, int end) {
            int len = end - start;
            RubyString result = runtime.newString(new ByteList(len));

            int relStart = start - byteList.begin();
            int relEnd = end - byteList.begin();

            int surrogateStart = -1;
            char surrogate = 0;

            for (int i = relStart; i < relEnd; ) {
                char c = byteList.charAt(i);
                if (c == '\\') {
                    i++;
                    if (i >= relEnd) {
                        return null;
                    }
                    c = byteList.charAt(i);
                    if (surrogateStart != -1 && c != 'u') {
                        throw Utils.newException(runtime, Utils.M_PARSER_ERROR,
                            "partial character in source, but hit end near ",
                            (ByteList)byteList.subSequence(surrogateStart, relEnd));
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
                            if (i > relEnd - 4) {
                                return null;
                            }
                            else {
                                String digits = byteList.subSequence(i, i + 4).toString();
                                int code = Integer.parseInt(digits, 16);
                                if (surrogateStart != -1) {
                                    if (Character.isLowSurrogate((char)code)) {
                                        int fullCode = Character.toCodePoint(surrogate, (char)code);
                                        result.cat(getUTF8Bytes(fullCode | 0L));
                                        surrogateStart = -1;
                                        surrogate = 0;
                                    }
                                    else {
                                        throw Utils.newException(runtime, Utils.M_PARSER_ERROR,
                                            "partial character in source, but hit end near ",
                                            (ByteList)byteList.subSequence(surrogateStart, relEnd));
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
                    throw Utils.newException(runtime, Utils.M_PARSER_ERROR,
                        "partial character in source, but hit end near ",
                        (ByteList)byteList.subSequence(surrogateStart, relEnd));
                }
                else {
                    int j = i;
                    while (j < relEnd && byteList.charAt(j) != '\\') j++;
                    result.cat(data, byteList.begin() + i, j - i);
                    i = j;
                }
            }
            if (surrogateStart != -1) {
                throw Utils.newException(runtime, Utils.M_PARSER_ERROR,
                    "partial character in source, but hit end near ",
                    (ByteList)byteList.subSequence(surrogateStart, relEnd));
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

        
// line 1440 "src/json/ext/Parser.java"
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


private static byte[] init__JSON_array_trans_targs_0()
{
	return new byte [] {
	    2,    0,    3,   13,   17,    3,    4,    9,    5,    6,    8,    7,
	   10,   12,   11,   14,   16,   15
	};
}

private static final byte _JSON_array_trans_targs[] = init__JSON_array_trans_targs_0();


private static byte[] init__JSON_array_trans_actions_0()
{
	return new byte [] {
	    0,    0,    1,    0,    3,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_array_trans_actions[] = init__JSON_array_trans_actions_0();


static final int JSON_array_start = 1;
static final int JSON_array_first_final = 17;
static final int JSON_array_error = 0;

static final int JSON_array_en_main = 1;

// line 652 "src/json/ext/Parser.rl"


        ParserResult parseArray(int p, int pe) {
            int cs = EVIL;

            if (parser.maxNesting > 0 && currentNesting > parser.maxNesting) {
                throw Utils.newException(runtime, Utils.M_NESTING_ERROR,
                    "nesting of " + currentNesting + " is too deep");
            }

            RubyArray result = runtime.newArray();

            
// line 1566 "src/json/ext/Parser.java"
	{
	cs = JSON_array_start;
	}
// line 665 "src/json/ext/Parser.rl"
            
// line 1572 "src/json/ext/Parser.java"
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
	cs = _JSON_array_trans_targs[_trans];

	if ( _JSON_array_trans_actions[_trans] != 0 ) {
		_acts = _JSON_array_trans_actions[_trans];
		_nacts = (int) _JSON_array_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_array_actions[_acts++] )
			{
	case 0:
// line 628 "src/json/ext/Parser.rl"
	{
                ParserResult res = parseValue(p, pe);
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
// line 639 "src/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 1669 "src/json/ext/Parser.java"
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
// line 666 "src/json/ext/Parser.rl"

            if (cs >= JSON_array_first_final) {
                return new ParserResult(result, p/*+1*/);
            }
            else {
                throw unexpectedToken(p, pe);
            }
        }

        
// line 1699 "src/json/ext/Parser.java"
private static byte[] init__JSON_object_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1,    1,    2
	};
}

private static final byte _JSON_object_actions[] = init__JSON_object_actions_0();


private static byte[] init__JSON_object_key_offsets_0()
{
	return new byte [] {
	    0,    0,    1,    8,   14,   16,   17,   19,   20,   36,   43,   49,
	   51,   52,   54,   55,   57,   58,   60,   61,   63,   64,   66,   67,
	   69,   70,   72,   73
	};
}

private static final byte _JSON_object_key_offsets[] = init__JSON_object_key_offsets_0();


private static char[] init__JSON_object_trans_keys_0()
{
	return new char [] {
	  123,   13,   32,   34,   47,  125,    9,   10,   13,   32,   47,   58,
	    9,   10,   42,   47,   42,   42,   47,   10,   13,   32,   34,   45,
	   47,   73,   78,   91,  102,  110,  116,  123,    9,   10,   48,   57,
	   13,   32,   44,   47,  125,    9,   10,   13,   32,   34,   47,    9,
	   10,   42,   47,   42,   42,   47,   10,   42,   47,   42,   42,   47,
	   10,   42,   47,   42,   42,   47,   10,   42,   47,   42,   42,   47,
	   10,    0
	};
}

private static final char _JSON_object_trans_keys[] = init__JSON_object_trans_keys_0();


private static byte[] init__JSON_object_single_lengths_0()
{
	return new byte [] {
	    0,    1,    5,    4,    2,    1,    2,    1,   12,    5,    4,    2,
	    1,    2,    1,    2,    1,    2,    1,    2,    1,    2,    1,    2,
	    1,    2,    1,    0
	};
}

private static final byte _JSON_object_single_lengths[] = init__JSON_object_single_lengths_0();


private static byte[] init__JSON_object_range_lengths_0()
{
	return new byte [] {
	    0,    0,    1,    1,    0,    0,    0,    0,    2,    1,    1,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0
	};
}

private static final byte _JSON_object_range_lengths[] = init__JSON_object_range_lengths_0();


private static byte[] init__JSON_object_index_offsets_0()
{
	return new byte [] {
	    0,    0,    2,    9,   15,   18,   20,   23,   25,   40,   47,   53,
	   56,   58,   61,   63,   66,   68,   71,   73,   76,   78,   81,   83,
	   86,   88,   91,   93
	};
}

private static final byte _JSON_object_index_offsets[] = init__JSON_object_index_offsets_0();


private static byte[] init__JSON_object_indicies_0()
{
	return new byte [] {
	    0,    1,    0,    0,    2,    3,    4,    0,    1,    5,    5,    6,
	    7,    5,    1,    8,    9,    1,   10,    8,   10,    5,    8,    5,
	    9,    7,    7,   11,   11,   12,   11,   11,   11,   11,   11,   11,
	   11,    7,   11,    1,   13,   13,   14,   15,    4,   13,    1,   14,
	   14,    2,   16,   14,    1,   17,   18,    1,   19,   17,   19,   14,
	   17,   14,   18,   20,   21,    1,   22,   20,   22,   13,   20,   13,
	   21,   23,   24,    1,   25,   23,   25,    7,   23,    7,   24,   26,
	   27,    1,   28,   26,   28,    0,   26,    0,   27,    1,    0
	};
}

private static final byte _JSON_object_indicies[] = init__JSON_object_indicies_0();


private static byte[] init__JSON_object_trans_targs_0()
{
	return new byte [] {
	    2,    0,    3,   23,   27,    3,    4,    8,    5,    7,    6,    9,
	   19,    9,   10,   15,   11,   12,   14,   13,   16,   18,   17,   20,
	   22,   21,   24,   26,   25
	};
}

private static final byte _JSON_object_trans_targs[] = init__JSON_object_trans_targs_0();


private static byte[] init__JSON_object_trans_actions_0()
{
	return new byte [] {
	    0,    0,    3,    0,    5,    0,    0,    0,    0,    0,    0,    1,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0
	};
}

private static final byte _JSON_object_trans_actions[] = init__JSON_object_trans_actions_0();


static final int JSON_object_start = 1;
static final int JSON_object_first_final = 27;
static final int JSON_object_error = 0;

static final int JSON_object_en_main = 1;

// line 713 "src/json/ext/Parser.rl"


        ParserResult parseObject(int p, int pe) {
            int cs = EVIL;
            RubyString lastName = null;

            if (parser.maxNesting > 0 && currentNesting > parser.maxNesting) {
                throw Utils.newException(runtime, Utils.M_NESTING_ERROR,
                    "nesting of " + currentNesting + " is too deep");
            }

            RubyHash result = RubyHash.newHash(runtime);

            
// line 1836 "src/json/ext/Parser.java"
	{
	cs = JSON_object_start;
	}
// line 727 "src/json/ext/Parser.rl"
            
// line 1842 "src/json/ext/Parser.java"
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
	_keys = _JSON_object_key_offsets[cs];
	_trans = _JSON_object_index_offsets[cs];
	_klen = _JSON_object_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _JSON_object_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _JSON_object_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _JSON_object_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _JSON_object_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _JSON_object_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	_trans = _JSON_object_indicies[_trans];
	cs = _JSON_object_trans_targs[_trans];

	if ( _JSON_object_trans_actions[_trans] != 0 ) {
		_acts = _JSON_object_trans_actions[_trans];
		_nacts = (int) _JSON_object_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_object_actions[_acts++] )
			{
	case 0:
// line 681 "src/json/ext/Parser.rl"
	{
                ParserResult res = parseValue(p, pe);
                if (res == null) {
                    { p += 1; _goto_targ = 5; if (true)  continue _goto;}
                }
                else {
                    result.op_aset(lastName, res.result);
                    {p = (( res.p - 1 /*+1*/))-1;}
                }
            }
	break;
	case 1:
// line 692 "src/json/ext/Parser.rl"
	{
                ParserResult res = parseString(p, pe);
                if (res == null) {
                    { p += 1; _goto_targ = 5; if (true)  continue _goto;}
                }
                else {
                    lastName = (RubyString)res.result;
                    {p = (( res.p - 1 /*+1*/))-1;}
                }
            }
	break;
	case 2:
// line 703 "src/json/ext/Parser.rl"
	{ { p += 1; _goto_targ = 5; if (true)  continue _goto;} }
	break;
// line 1952 "src/json/ext/Parser.java"
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
// line 728 "src/json/ext/Parser.rl"

            if (cs < JSON_object_first_final) {
                return null;
            }

            IRubyObject returnedResult = result;

            // attempt to de-serialize object
            if (parser.createId != null) {
                IRubyObject vKlassName = result.op_aref(runtime.getCurrentContext(), parser.createId);
                if (!vKlassName.isNil()) {
                    String klassName = vKlassName.asJavaString();
                    RubyModule klass;
                    try {
                        klass = runtime.getClassFromPath(klassName);
                    }
                    catch (RaiseException e) {
                        if (runtime.getClass("NameError").isInstance(e.getException())) {
                            // invalid class path, but we're supposed to throw ArgumentError
                            throw runtime.newArgumentError("undefined class/module " + klassName);
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

        
// line 2010 "src/json/ext/Parser.java"
private static byte[] init__JSON_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1
	};
}

private static final byte _JSON_actions[] = init__JSON_actions_0();


private static byte[] init__JSON_key_offsets_0()
{
	return new byte [] {
	    0,    0,    7,    9,   10,   12,   13,   15,   16,   18,   19
	};
}

private static final byte _JSON_key_offsets[] = init__JSON_key_offsets_0();


private static char[] init__JSON_trans_keys_0()
{
	return new char [] {
	   13,   32,   47,   91,  123,    9,   10,   42,   47,   42,   42,   47,
	   10,   42,   47,   42,   42,   47,   10,   13,   32,   47,    9,   10,
	    0
	};
}

private static final char _JSON_trans_keys[] = init__JSON_trans_keys_0();


private static byte[] init__JSON_single_lengths_0()
{
	return new byte [] {
	    0,    5,    2,    1,    2,    1,    2,    1,    2,    1,    3
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
	    0,    0,    7,   10,   12,   15,   17,   20,   22,   25,   27
	};
}

private static final byte _JSON_index_offsets[] = init__JSON_index_offsets_0();


private static byte[] init__JSON_indicies_0()
{
	return new byte [] {
	    0,    0,    2,    3,    4,    0,    1,    5,    6,    1,    7,    5,
	    7,    0,    5,    0,    6,    8,    9,    1,   10,    8,   10,   11,
	    8,   11,    9,   11,   11,   12,   11,    1,    0
	};
}

private static final byte _JSON_indicies[] = init__JSON_indicies_0();


private static byte[] init__JSON_trans_targs_0()
{
	return new byte [] {
	    1,    0,    2,   10,   10,    3,    5,    4,    7,    9,    8,   10,
	    6
	};
}

private static final byte _JSON_trans_targs[] = init__JSON_trans_targs_0();


private static byte[] init__JSON_trans_actions_0()
{
	return new byte [] {
	    0,    0,    0,    3,    1,    0,    0,    0,    0,    0,    0,    0,
	    0
	};
}

private static final byte _JSON_trans_actions[] = init__JSON_trans_actions_0();


static final int JSON_start = 1;
static final int JSON_first_final = 10;
static final int JSON_error = 0;

static final int JSON_en_main = 1;

// line 799 "src/json/ext/Parser.rl"


        public IRubyObject parse() {
            int cs = EVIL;
            int p, pe;
            IRubyObject result = null;

            
// line 2122 "src/json/ext/Parser.java"
	{
	cs = JSON_start;
	}
// line 807 "src/json/ext/Parser.rl"
            p = byteList.begin();
            pe = p + byteList.length();
            
// line 2130 "src/json/ext/Parser.java"
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
	cs = _JSON_trans_targs[_trans];

	if ( _JSON_trans_actions[_trans] != 0 ) {
		_acts = _JSON_trans_actions[_trans];
		_nacts = (int) _JSON_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _JSON_actions[_acts++] )
			{
	case 0:
// line 771 "src/json/ext/Parser.rl"
	{
                currentNesting = 1;
                ParserResult res = parseObject(p, pe);
                if (res == null) {
                    { p += 1; _goto_targ = 5; if (true)  continue _goto;}
                }
                else {
                    result = res.result;
                    {p = (( res.p - 1 +1))-1;}
                }
            }
	break;
	case 1:
// line 783 "src/json/ext/Parser.rl"
	{
                currentNesting = 1;
                ParserResult res = parseArray(p, pe);
                if (res == null) {
                    { p += 1; _goto_targ = 5; if (true)  continue _goto;}
                }
                else {
                    result = res.result;
                    {p = (( res.p - 1 +1))-1;}
                }
            }
	break;
// line 2238 "src/json/ext/Parser.java"
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
// line 810 "src/json/ext/Parser.rl"

            if (cs >= JSON_first_final && p == pe) {
                return result;
            }
            else {
                throw unexpectedToken(p, pe);
            }
        }

        /**
         * Retrieves a constant directly descended from the <code>JSON</code> module.
         * @param name The constant name
         */
        private IRubyObject getConstant(String name) {
            return runtime.getModule("JSON").getConstant(name);
        }
    }
}
