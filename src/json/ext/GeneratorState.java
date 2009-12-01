/*
 * This code is copyrighted work by Daniel Luz <dev at mernen dot com>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import org.jruby.Ruby;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyHash;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * The <code>JSON::Ext::Generator::State</code> class.
 * 
 * <p>This class is used to create State instances, that are use to hold data
 * while generating a JSON text from a a Ruby data structure.
 * 
 * @author mernen
 */
public class GeneratorState extends RubyObject {
    /**
     * The indenting unit string. Will be repeated several times for larger
     * indenting levels.
     */
    private RubyString indent;
    /**
     * The spacing to be added after a semicolon on a JSON object.
     * @see #spaceBefore
     */
    private RubyString space;
    /**
     * The spacing to be added before a semicolon on a JSON object.
     * @see #space
     */
    private RubyString spaceBefore;
    /**
     * Any suffix to be added after the comma for each element on a JSON object.
     * It is assumed to be a newline, if set.
     */
    private RubyString objectNl;
    /**
     * Any suffix to be added after the comma for each element on a JSON Array.
     * It is assumed to be a newline, if set.
     */
    private RubyString arrayNl;

    /**
     * The maximum level of nesting of structures allowed.
     * <code>0</code> means disabled.
     */
    private int maxNesting;
    /**
     * Whether special float values (<code>NaN</code>, <code>Infinity</code>,
     * <code>-Infinity</code>) are accepted.
     * If set to <code>false</code>, an exception will be thrown upon
     * encountering one.
     */
    private boolean allowNaN;
    // Porting note: due to the use of inner anonymous classes in the generator
    // methods, the "memo", "depth" and "flag" fields are not needed

    static final ObjectAllocator ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
            return new GeneratorState(runtime, klazz);
        }
    };

    public GeneratorState(Ruby runtime, RubyClass metaClass) {
        super(runtime, metaClass);
    }

    /**
     * <code>State.from_state(opts)</code>
     * 
     * <p>Creates a State object from <code>opts</code>, which ought to be
     * {@link RubyHash Hash} to create a new <code>State</code> instance
     * configured by <codes>opts</code>, something else to create an
     * unconfigured instance. If <code>opts</code> is a <code>State</code>
     * object, it is just returned.
     * @param clazzParam The receiver of the method call
     *                   ({@link RubyClass} <code>State</code>)
     * @param opts The object to use as a base for the new <code>State</code>
     * @param block The block passed to the method
     * @return A <code>GeneratorState</code> as determined above
     */
    @JRubyMethod(required = 1, meta = true)
    public static IRubyObject from_state(ThreadContext context,
            IRubyObject clazzParam, IRubyObject opts, Block block) {
        // if the given parameter is a Generator::State, return itself
        RubyModule clazz = (RubyModule)clazzParam;
        if (clazz.isInstance(opts)) {
            return (GeneratorState)opts;
        }

        Ruby runtime = clazz.getRuntime();
        // if the given parameter is a Hash, pass it to the instantiator
        if (runtime.getHash().isInstance(opts)) {
            return clazz.callMethod(context, "new", opts);
        }

        // ignore any other kinds of parameter
        return clazz.callMethod(context, "new");
    }

    /**
     * <code>State#initialize(opts = {})</code>
     * 
     * Instantiates a new <code>State</code> object, configured by <code>opts</code>.
     * 
     * <code>opts</code> can have the following keys:
     * 
     * <dl>
     * <dt><code>:indent</code>
     * <dd>a {@link RubyString String} used to indent levels (default: <code>""</code>)
     * <dt><code>:space</code>
     * <dd>a String that is put after a <code>':'</code> or <code>','</code>
     * delimiter (default: <code>""</code>)
     * <dt><code>:space_before</code>
     * <dd>a String that is put before a <code>":"</code> pair delimiter
     * (default: <code>""</code>)
     * <dt><code>:object_nl</code>
     * <dd>a String that is put at the end of a JSON object (default: <code>""</code>) 
     * <dt><code>:array_nl</code>
     * <dd>a String that is put at the end of a JSON array (default: <code>""</code>)
     * <dt><code>:allow_nan</code>
     * <dd><code>true</code> if <code>NaN</code>, <code>Infinity</code>, and
     * <code>-Infinity</code> should be generated, otherwise an exception is
     * thrown if these values are encountered.
     * This options defaults to <code>false</code>.
     */
    @JRubyMethod(rest = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = getRuntime();
        indent = runtime.newString();
        space = runtime.newString();
        spaceBefore = runtime.newString();
        arrayNl = runtime.newString();
        objectNl = runtime.newString();
        if (args.length == 0 || args[0].isNil()) {
            allowNaN = false;
            maxNesting = 19;
        }
        else {
            configure(context, args[0]);
        }
        return this;
    }

    /**
     * XXX
     */
    @JRubyMethod(required = 1)
    public IRubyObject generate(ThreadContext context, IRubyObject obj) {
        RubyString result = Utils.toJson(context, obj, this);
        if (!objectOrArrayLiteral(result)) {
            throw Utils.newException(context, Utils.M_GENERATOR_ERROR,
                    "only generation of JSON objects or arrays allowed");
        }
        return result;
    }

    /**
     * Ensures the given string is in the form "[...]" or "{...}", being
     * possibly surrounded by white space.
     * The string's encoding must be ASCII-compatible.
     * @param value
     * @return
     */
    private static boolean objectOrArrayLiteral(RubyString value) {
        ByteList bl = value.getByteList();
        int len = bl.length();

        for (int pos = 0; pos < len - 1; pos++) {
            int b = bl.get(pos);
            if (Character.isWhitespace(b)) continue;

            // match the opening brace
            switch (b) {
            case '[':
                return matchClosingBrace(bl, pos, len, ']');
            case '{':
                return matchClosingBrace(bl, pos, len, '}');
            default:
                return false;
            }
        }
        return false;
    }

    private static boolean matchClosingBrace(ByteList bl, int pos, int len,
                                             int brace) {
        for (int endPos = len - 1; endPos > pos; endPos--) {
            int b = bl.get(endPos);
            if (Character.isWhitespace(b)) continue;
            if (b == brace) return true;
            return false;
        }
        return false;
    }

    @JRubyMethod(name = "indent")
    public RubyString indent_get() {
        return indent;
    }

    @JRubyMethod(name = "indent=", required = 1)
    public IRubyObject indent_set(IRubyObject indent) {
        this.indent = indent.convertToString();
        return indent;
    }

    @JRubyMethod(name = "space")
    public RubyString space_get() {
        return space;
    }

    @JRubyMethod(name = "space=", required = 1)
    public IRubyObject space_set(IRubyObject space) {
        this.space = space.convertToString();
        return space;
    }

    @JRubyMethod(name = "space_before")
    public RubyString space_before_get() {
        return spaceBefore;
    }

    @JRubyMethod(name = "space_before=", required = 1)
    public IRubyObject space_before_set(IRubyObject spaceBefore) {
        this.spaceBefore = spaceBefore.convertToString();
        return spaceBefore;
    }

    @JRubyMethod(name = "object_nl")
    public RubyString object_nl_get() {
        return objectNl;
    }

    @JRubyMethod(name = "object_nl=", required = 1)
    public IRubyObject object_nl_set(IRubyObject objectNl) {
        this.objectNl = objectNl.convertToString();
        return objectNl;
    }

    @JRubyMethod(name = "array_nl")
    public RubyString array_nl_get() {
        return arrayNl;
    }

    @JRubyMethod(name = "array_nl=", required = 1)
    public IRubyObject array_nl_set(IRubyObject arrayNl) {
        this.arrayNl = arrayNl.convertToString();
        return arrayNl;
    }

    @JRubyMethod(name = "max_nesting")
    public RubyInteger max_nesting_get() {
        return getRuntime().newFixnum(maxNesting);
    }

    @JRubyMethod(name = "max_nesting=", required = 1)
    public IRubyObject max_nesting_set(IRubyObject max_nesting) {
        maxNesting = RubyNumeric.fix2int(max_nesting);
        return max_nesting;
    }

    public boolean allowNaN() {
        return allowNaN;
    }

    /**
     * @see #allowNaN()
     */
    @JRubyMethod(name = "allow_nan?")
    public RubyBoolean allow_nan_p() {
        return getRuntime().newBoolean(allowNaN);
    }

    /**
     * <code>State#configure(opts)</code>
     * 
     * <p>Configures this State instance with the {@link RubyHash Hash}
     * <code>opts</code>, and returns itself.
     * @param vOpts The options hash
     * @return The receiver
     */
    @JRubyMethod(required = 1)
    public IRubyObject configure(ThreadContext context, IRubyObject vOpts) {
        RubyHash opts;
        if (vOpts.respondsTo("to_hash")) {
            opts = vOpts.convertToHash();
        }
        else {
            opts = vOpts.callMethod(context, "to_h").convertToHash();
        }

        RubyString vIndent = Utils.fastGetSymString(opts, "indent");
        if (vIndent != null) indent = vIndent;

        RubyString vSpace = Utils.fastGetSymString(opts, "space");
        if (vSpace != null) space = vSpace;

        RubyString vSpaceBefore = Utils.fastGetSymString(opts, "space_before");
        if (vSpaceBefore != null) spaceBefore = vSpaceBefore;

        RubyString vArrayNl = Utils.fastGetSymString(opts, "array_nl");
        if (vArrayNl != null) arrayNl = vArrayNl;

        RubyString vObjectNl = Utils.fastGetSymString(opts, "object_nl");
        if (vObjectNl != null) objectNl = vObjectNl;

        IRubyObject vMaxNesting = Utils.fastGetSymItem(opts, "max_nesting");
        if (vMaxNesting != null) {
            maxNesting = vMaxNesting.isTrue() ? RubyNumeric.fix2int(vMaxNesting) : 0;
        }
        else {
            maxNesting = 19;
        }

        IRubyObject vAllowNaN = Utils.fastGetSymItem(opts, "allow_nan");
        allowNaN = vAllowNaN != null && vAllowNaN.isTrue();

        return this;
    }

    /**
     * <code>State#to_h()</code>
     * 
     * <p>Returns the configuration instance variables as a hash, that can be
     * passed to the configure method.
     * @return
     */
    @JRubyMethod
    public RubyHash to_h(ThreadContext context) {
        Ruby runtime = getRuntime();
        RubyHash result = RubyHash.newHash(runtime);

        result.op_aset(context, runtime.newSymbol("indent"), indent_get());
        result.op_aset(context, runtime.newSymbol("space"), space_get());
        result.op_aset(context, runtime.newSymbol("space_before"), space_before_get());
        result.op_aset(context, runtime.newSymbol("object_nl"), object_nl_get());
        result.op_aset(context, runtime.newSymbol("array_nl"), array_nl_get());
        result.op_aset(context, runtime.newSymbol("allow_nan"), allow_nan_p());
        result.op_aset(context, runtime.newSymbol("max_nesting"), max_nesting_get());
        return result;
    }

    /**
     * Returns the maximum level of nesting configured for this state.
     * @return
     */
    public int getMaxNesting() {
        return maxNesting;
    }

    /**
     * Checks if the current depth is allowed as per this state's options.
     * @param depth The corrent depth
     */
    void checkMaxNesting(int depth) {
        if (getMaxNesting() != 0 && depth > getMaxNesting()) {
            throw Utils.newException(getRuntime().getCurrentContext(),
                    Utils.M_NESTING_ERROR, "nesting of " + depth + " is too deep");
        }
    }
}
