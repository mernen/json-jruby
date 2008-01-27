package json.ext;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.runtime.load.BasicLibraryService;

public class ParserService implements BasicLibraryService {
	public boolean basicLoad(Ruby runtime) throws IOException {
		runtime.getLoadService().require("json/common");

		RubyModule jsonModule = runtime.defineModule("JSON");
		RubyModule jsonExtModule = jsonModule.defineModuleUnder("Ext");
		RubyClass parserClass = jsonExtModule.defineClassUnder("Parser", runtime.getObject(), Parser.ALLOCATOR);
		parserClass.defineAnnotatedMethods(Parser.class);
		return true;
	}
}
