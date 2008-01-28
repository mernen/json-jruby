package json.ext;

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
	private static final long serialVersionUID = -5070972964324653291L;

	private RubyString indent;
	private RubyString space;
	private RubyString spaceBefore;
	private RubyString objectNl;
	private RubyString arrayNl;
	private boolean checkCircular;
	private Set<Long> seen;
	private RubyString memo;
	private int depth;
	private int maxNesting;
	private boolean flag;
	private boolean allowNaN;

	static final ObjectAllocator ALLOCATOR = new ObjectAllocator() {
		public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
			return new GeneratorState(runtime, klazz);
		}
	};

	public GeneratorState(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	/**
	 * Creates a State object from <code>opts</code>, which ought to be
	 * {@link RubyHash Hash} to create a new <code>State</code> instance
	 * configured by <codes>opts</code>, something else to create an
	 * unconfigured instance. If <code>opts</code> is a <code>State</code>
	 * object, it is just returned.
	 * @param clazzParam The receiver of the method call
	 *                   ({@link RubyClass} <code>State</code>)
	 * @param opts The object to use as a base for the new <code>State</code>
	 * @param block The block passed to the method
	 * @return
	 */
	@JRubyMethod(name = "from_state", required = 1, meta = true)
	public static IRubyObject from_state(IRubyObject clazzParam, IRubyObject opts, Block block) {
		RubyModule clazz = (RubyModule)clazzParam;
		if (clazz.isInstance(opts)) {
			return (GeneratorState)opts;
		}
		if (clazz.getRuntime().getHash().isInstance(opts)) {
			return (GeneratorState)clazz.callMethod(clazz.getRuntime().getCurrentContext(), "new", opts);
		}
		return (GeneratorState)clazz.callMethod(clazz.getRuntime().getCurrentContext(), "new");
	}

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
		seen = new HashSet<Long>();
		memo = null;
		depth = 0;
		return this;
	}

	/**
	 * Ruby getter for the {@link #indent} attribute
	 * @return The defined prefix indenting unit for each line
	 */
	@JRubyMethod(name = "indent")
	public RubyString indent_get() {
		return indent;
	}

	@JRubyMethod(name = "indent=", required = 1)
	public IRubyObject indent_set(IRubyObject indent) {
		this.indent = indent.convertToString();
		return indent;
	}

	/**
	 * Ruby getter for the {@link #space} attribute
	 * @return The defined spacing after each {@link RubyHash Hash} semicolon
	 */
	@JRubyMethod(name = "space")
	public RubyString space_get() {
		return space;
	}

	@JRubyMethod(name = "space=", required = 1)
	public IRubyObject space_set(IRubyObject space) {
		this.space = space.convertToString();
		return space;
	}

	/**
	 * Ruby getter for the {@link #space} attribute
	 * @return The defined spacing before each {@link RubyHash Hash} semicolon
	 */
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

	@JRubyMethod(name = "allow_nan?")
	public RubyBoolean allow_nan_p() {
		return getRuntime().newBoolean(allowNaN);
	}

	private static long getId(IRubyObject object) {
		return object.getRuntime().getObjectSpace().idOf(object);
	}

	public boolean hasSeen(IRubyObject object) {
		return seen.contains(getId(object));
	}

	@JRubyMethod(name = "seen?", required = 1)
	public IRubyObject seen_p(IRubyObject object) {
		return hasSeen(object) ? getRuntime().getTrue() : getRuntime().getNil();
	}

	public void remember(IRubyObject object) {
		seen.add(getId(object));
	}

	@JRubyMethod(name = "remember", required = 1)
	public IRubyObject rb_remember(IRubyObject object) {
		remember(object);
		return getRuntime().getTrue();
	}

	@JRubyMethod(name = "forget", required = 1)
	public IRubyObject forget(IRubyObject object) {
		return seen.remove(getId(object)) ? getRuntime().getTrue() : getRuntime().getNil();
	}

	@JRubyMethod(name = "configure", required = 1)
	public GeneratorState configure(IRubyObject vOpts) {
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
		checkCircular = vCheckCircular == null ? true : vCheckCircular.isTrue();

		IRubyObject vMaxNesting = Utils.fastGetSymItem(opts, "max_nesting");
		if (vMaxNesting != null) {
			maxNesting = vMaxNesting.isTrue() ? RubyNumeric.fix2int(vMaxNesting) : 0;
		}

		IRubyObject vAllowNaN = Utils.fastGetSymItem(opts, "allow_nan");
		allowNaN = vAllowNaN != null && vAllowNaN.isTrue();

		return this;
	}

	@JRubyMethod(name = "to_h")
	public RubyHash to_h() {
		RubyHash result = RubyHash.newHash(getRuntime());

		result.op_aset(getRuntime().newSymbol("indent"), indent_get());
		result.op_aset(getRuntime().newSymbol("space"), space_get());
		result.op_aset(getRuntime().newSymbol("space_before"), space_before_get());
		result.op_aset(getRuntime().newSymbol("object_nl"), object_nl_get());
		result.op_aset(getRuntime().newSymbol("array_nl"), array_nl_get());
		result.op_aset(getRuntime().newSymbol("check_circular"), check_circular_p());
		result.op_aset(getRuntime().newSymbol("allow_nan"), allow_nan_p());
		result.op_aset(getRuntime().newSymbol("max_nesting"), max_nesting_get());
		return result;
	}

	public RubyString getMemo() {
		return memo;
	}

	public void setMemo(RubyString memo) {
		this.memo = memo;
	}

	public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
	}

	public boolean getStateFlag() {
		return flag;
	}

	public void setStateFlag(boolean flag) {
		this.flag = flag;
	}

	public int getMaxNesting() {
		return maxNesting;
	}

	public boolean checkCircular() {
		return checkCircular;
	}
}
