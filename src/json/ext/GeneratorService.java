package json.ext;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.runtime.load.BasicLibraryService;

public class GeneratorService implements BasicLibraryService {
	public boolean basicLoad(Ruby runtime) throws IOException {
		runtime.getLoadService().require("json/common");

		RubyModule jsonModule = runtime.defineModule("JSON");
		RubyModule jsonExtModule = jsonModule.defineModuleUnder("Ext");
		RubyModule generatorModule = jsonExtModule.defineModuleUnder("Generator");

		RubyClass stateClass = generatorModule.defineClassUnder("State", runtime.getObject(), GeneratorState.ALLOCATOR);
		stateClass.defineAnnotatedMethods(GeneratorState.class);

		return true;
	}
}
