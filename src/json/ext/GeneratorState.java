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
import org.jruby.runtime.builtin.IRubyObject;

public class GeneratorState extends RubyObject {
	private RubyString indent;
	private RubyString space;
	private RubyString spaceBefore;
	private RubyString objectNl;
	private RubyString arrayNl;
	private boolean checkCircular;
	private RubyHash seen;
	private RubyString memo;
	private RubyInteger depth;
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

	@JRubyMethod(name = "allow_nan?")
	public RubyBoolean allow_nan_p() {
		return getRuntime().newBoolean(allowNaN);
	}

	@JRubyMethod(name = "seen?", required = 1)
	public IRubyObject seen_p(IRubyObject object) {
		return seen.op_aref(object.id());
	}

	@JRubyMethod(name = "remember", required = 1)
	public IRubyObject remember(IRubyObject object) {
		return seen.op_aset(object.id(), getRuntime().getTrue());
	}

	@JRubyMethod(name = "forget", required = 1)
	public IRubyObject forget(IRubyObject object) {
		return seen.delete(object.id(), Block.NULL_BLOCK);
	}

	@JRubyMethod(name = "configure", required = 1)
	public GeneratorState configure(IRubyObject opts) {
		// TODO
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
}
