package com.mernen.json.ext;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.runtime.builtin.IRubyObject;

public abstract class Init {
	public static void prepare(IRubyObject obj) {
		Ruby runtime = obj.getRuntime();
		Parser.load(runtime);

		loadGeneratorModule(runtime);
	}

	static void loadGeneratorModule(Ruby runtime) {
		runtime.getLoadService().require("json/common");

		RubyModule jsonModule = runtime.defineModule("JSON");
		RubyModule jsonExtModule = jsonModule.defineModuleUnder("Ext");
		jsonExtModule.defineModuleUnder("Generator");

		GeneratorState.load(runtime);
	}
}
