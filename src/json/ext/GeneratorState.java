/*
 * This code is copyrighted work by Daniel Luz <mernen at gmail dot com>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

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
     * Whether the generator should check for circular references.
     * Disabling them may improve performance, but the library user must then
     * ensure no circular references will happen.
     */
    private boolean checkCircular;
    /**
     * Internal set of objects that are currently on the stack of inspection.
     * Used to detect circular references.
     */
    private final Set<Long> seen = new HashSet<Long>();
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
    @JRubyMethod(name = "from_state", required = 1, meta = true)
    public static IRubyObject from_state(IRubyObject clazzParam, IRubyObject opts,
            Block block) {
        // if the given parameter is a Generator::State, return itself
        RubyModule clazz = (RubyModule)clazzParam;
        if (clazz.isInstance(opts)) {
            return (GeneratorState)opts;
        }

        Ruby runtime = clazz.getRuntime();
        // if the given parameter is a Hash, pass it to the instantiator
        if (runtime.getHash().isInstance(opts)) {
            return clazz.callMethod(runtime.getCurrentContext(), "new", opts);
        }

        // ignore any other kinds of parameter
        return clazz.callMethod(runtime.getCurrentContext(), "new");
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
     * <dt><code>:check_circular</code>
     * <dd><code>true</code> if checking for circular data structures should be
     * done, <code>false</code> (the default) otherwise.
     * <dt><code>:allow_nan</code>
     * <dd><code>true</code> if <code>NaN</code>, <code>Infinity</code>, and
     * <code>-Infinity</code> should be generated, otherwise an exception is
     * thrown if these values are encountered.
     * This options defaults to <code>false</code>.
     */
    @JRubyMethod(name = "initialize", rest = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(IRubyObject[] args) {
        Ruby runtime = getRuntime();
        indent = runtime.newString();
        space = runtime.newString();
        spaceBefore = runtime.newString();
        arrayNl = runtime.newString();
        objectNl = runtime.newString();
        if (args.length == 0 || args[0].isNil()) {
            checkCircular = true;
            allowNaN = false;
            maxNesting = 19;
        }
        else {
            configure(args[0]);
        }
        return this;
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

    @JRubyMethod(name = "check_circular?")
    public RubyBoolean check_circular_p() {
        return getRuntime().newBoolean(checkCircular);
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
     * Convenience method for the "seen" methods.
     * @param object The object to process
     * @return The object's Ruby ID
     * @see #hasSeen(IRubyObject)
     * @see #remember(IRubyObject)
     * @see #forget(IRubyObject)
     */
    private static long getId(IRubyObject object) {
        return object.getRuntime().getObjectSpace().idOf(object);
    }

    /**
     * Checks whether an object is part of the current chain of recursive JSON
     * generation.
     * @param object The object to check
     * @return Whether the object is part of the current chain of recursive
     *         JSON generation or not
     */
    public boolean hasSeen(IRubyObject object) {
        return seen.contains(getId(object));
    }

    /**
     * @return {@link Ruby#getTrue() true} if the object is part of the current
     *         chain of recursive JSON generation, or {@link Ruby#getNil() nil}
     *         if not
     * @see #hasSeen(IRubyObject)
     */
    @JRubyMethod(name = "seen?", required = 1)
    public IRubyObject seen_p(IRubyObject object) {
        return hasSeen(object) ? getRuntime().getTrue() : getRuntime().getNil();
    }

    /**
     * Adds an object to the stack.
     * @param object The object being inspected
     */
    public void remember(IRubyObject object) {
        seen.add(getId(object));
    }

    /**
     * @return {@link Ruby#getTrue() true}
     * @see #remember(IRubyObject)
     */
    @JRubyMethod(name = "remember", required = 1)
    public IRubyObject rb_remember(IRubyObject object) {
        remember(object);
        return getRuntime().getTrue();
    }

    public boolean forget(IRubyObject object) {
        return seen.remove(getId(object));
    }

    /**
     * @see #forget(IRubyObject)
     */
    @JRubyMethod(name = "forget", required = 1)
    public IRubyObject rb_forget(IRubyObject object) {
        return forget(object) ? getRuntime().getTrue() : getRuntime().getNil();
    }

    /**
     * <code>State#configure(opts)</code>
     * 
     * <p>Configures this State instance with the {@link RubyHash Hash}
     * <code>opts</code>, and returns itself.
     * @param vOpts The options hash
     * @return The receiver
     */
    @JRubyMethod(name = "configure", required = 1)
    public IRubyObject configure(IRubyObject vOpts) {
        RubyHash opts;
        if (vOpts.respondsTo("to_hash")) {
            opts = vOpts.convertToHash();
        }
        else {
            opts = vOpts.callMethod(getRuntime().getCurrentContext(), "to_h").convertToHash();
        }

        RubyString vIndent = Utils.getSymString(opts, "indent");
        if (vIndent != null) indent = vIndent;

        RubyString vSpace = Utils.getSymString(opts, "space");
        if (vSpace != null) space = vSpace;

        RubyString vSpaceBefore = Utils.getSymString(opts, "space_before");
        if (vSpaceBefore != null) spaceBefore = vSpaceBefore;

        RubyString vArrayNl = Utils.getSymString(opts, "array_nl");
        if (vArrayNl != null) arrayNl = vArrayNl;

        RubyString vObjectNl = Utils.getSymString(opts, "object_nl");
        if (vObjectNl != null) objectNl = vObjectNl;

        IRubyObject vCheckCircular = Utils.fastGetSymItem(opts, "check_circular");
        checkCircular = vCheckCircular == null || vCheckCircular.isTrue();

        IRubyObject vMaxNesting = Utils.fastGetSymItem(opts, "max_nesting");
        if (vMaxNesting != null) {
            maxNesting = vMaxNesting.isTrue() ? RubyNumeric.fix2int(vMaxNesting) : 0;
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
    @JRubyMethod(name = "to_h")
    public RubyHash to_h() {
        Ruby runtime = getRuntime();
        RubyHash result = RubyHash.newHash(runtime);

        result.op_aset(runtime.newSymbol("indent"), indent_get());
        result.op_aset(runtime.newSymbol("space"), space_get());
        result.op_aset(runtime.newSymbol("space_before"), space_before_get());
        result.op_aset(runtime.newSymbol("object_nl"), object_nl_get());
        result.op_aset(runtime.newSymbol("array_nl"), array_nl_get());
        result.op_aset(runtime.newSymbol("check_circular"), check_circular_p());
        result.op_aset(runtime.newSymbol("allow_nan"), allow_nan_p());
        result.op_aset(runtime.newSymbol("max_nesting"), max_nesting_get());
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
     * Returns whether circular reference checking should be performed.
     * @return
     */
    public boolean checkCircular() {
        return checkCircular;
    }

    /**
     * Checks if the current depth is allowed as per this state's options.
     * @param depth The corrent depth
     */
    void checkMaxNesting(int depth) {
        if (getMaxNesting() != 0 && depth > getMaxNesting()) {
            throw Utils.newException(getRuntime(), Utils.M_NESTING_ERROR,
                "nesting of " + depth + " is too deep");
        }
    }
}
