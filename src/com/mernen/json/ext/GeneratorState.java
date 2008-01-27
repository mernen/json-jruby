package com.mernen.json.ext;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyHash;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
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

	private static final ObjectAllocator STATE_ALLOCATOR = new ObjectAllocator() {
		public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
			return new GeneratorState(runtime, klazz);
		}
	};

	static void load(Ruby runtime) {
		RubyModule generatorModule = runtime.getClassFromPath("JSON::Ext::Generator");
		RubyClass stateClass = generatorModule.defineClassUnder("State", runtime.getObject(), STATE_ALLOCATOR);

		stateClass.defineAnnotatedMethods(GeneratorState.class);
	}

	public GeneratorState(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(name = "indent")
	public RubyString getIndent() {
		return indent;
	}

	@JRubyMethod(name = "indent=", required = 1)
	public IRubyObject setIndent(IRubyObject indent) {
		this.indent = indent.convertToString();
		return indent;
	}

	@JRubyMethod(name = "space")
	public RubyString getSpace() {
		return space;
	}

	@JRubyMethod(name = "space=", required = 1)
	public IRubyObject setSpace(IRubyObject space) {
		this.space = space.convertToString();
		return space;
	}

	@JRubyMethod(name = "space_before")
	public RubyString getSpaceBefore() {
		return spaceBefore;
	}

	@JRubyMethod(name = "space_before=", required = 1)
	public IRubyObject setSpaceBefore(IRubyObject spaceBefore) {
		this.spaceBefore = spaceBefore.convertToString();
		return spaceBefore;
	}
}
