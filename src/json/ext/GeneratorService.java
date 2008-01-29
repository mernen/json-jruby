/*
 * This code is copyrighted work by Daniel Luz <@gmail.com: mernen>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.runtime.load.BasicLibraryService;

/**
 * The service invoked by JRuby's {@link org.jruby.runtime.load.LoadService LoadService}.
 * Defines the <code>JSON::Ext::Generator</code> module.
 * @author mernen
 */
public class GeneratorService implements BasicLibraryService {
	public boolean basicLoad(Ruby runtime) throws IOException {
		runtime.getLoadService().require("json/common");

		RubyModule jsonModule = runtime.defineModule("JSON");
		RubyModule jsonExtModule = jsonModule.defineModuleUnder("Ext");
		RubyModule generatorModule = jsonExtModule.defineModuleUnder("Generator");

		RubyClass stateClass = generatorModule.defineClassUnder("State", runtime.getObject(), GeneratorState.ALLOCATOR);
		stateClass.defineAnnotatedMethods(GeneratorState.class);

		RubyModule generatorMethods = generatorModule.defineModuleUnder("GeneratorMethods");
		new GeneratorMethodsLoader(generatorMethods).load();

		return true;
	}
}
