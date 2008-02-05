/*
 * This code is copyrighted work by Daniel Luz <mernen at gmail dot com>.
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
 * Defines the <code>JSON::Ext::Parser</code> class.
 * @author mernen
 */
public class ParserService implements BasicLibraryService {
    public boolean basicLoad(Ruby runtime) throws IOException {
        runtime.getLoadService().require("json/common");

        RubyModule jsonModule = runtime.defineModule("JSON");
        RubyModule jsonExtModule = jsonModule.defineModuleUnder("Ext");
        RubyClass parserClass =
            jsonExtModule.defineClassUnder("Parser", runtime.getObject(), Parser.ALLOCATOR);
        parserClass.defineAnnotatedMethods(Parser.class);
        return true;
    }
}
