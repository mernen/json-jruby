/*
 * This code is copyrighted work by Daniel Luz <dev at mernen dot com>.
 *
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyEncoding;
import org.jruby.RubyModule;
import org.jruby.runtime.ThreadContext;


class RuntimeInfo {
    private static final Map<Ruby, RuntimeInfo> runtimes =
            // most of the time there's just one single runtime
            new WeakHashMap<Ruby, RuntimeInfo>(1);

    // these fields are filled by the service loaders
    /** JSON */
    RubyModule jsonModule;
    /** JSON::Ext::Generator::GeneratorMethods::String::Extend */
    RubyModule stringExtendModule;
    /** JSON::Ext::Generator::State */
    RubyClass generatorStateClass;

    final RubyEncoding utf8;
    final RubyEncoding ascii8bit;
    // other encodings
    private final Map<String, RubyEncoding> encodings;

    private RuntimeInfo(Ruby runtime) {
        RubyClass encodingClass = runtime.getEncoding();
        if (encodingClass == null) { // 1.8 mode
            utf8 = ascii8bit = null;
            encodings = null;
        } else {
            ThreadContext context = runtime.getCurrentContext();

            utf8 = (RubyEncoding)RubyEncoding.find(context,
                    encodingClass, runtime.newString("utf-8"));
            ascii8bit = (RubyEncoding)RubyEncoding.find(context,
                    encodingClass, runtime.newString("ascii-8bit"));
            encodings = new HashMap<String, RubyEncoding>();
        }
    }

    static RuntimeInfo initRuntime(Ruby runtime) {
        synchronized (runtimes) {
            RuntimeInfo cache = runtimes.get(runtime);
            if (cache == null) {
                cache = new RuntimeInfo(runtime);
                runtimes.put(runtime, cache);
            }
            return cache;
        }
    }

    public static RuntimeInfo forRuntime(Ruby runtime) {
        synchronized (runtimes) {
            RuntimeInfo cache = runtimes.get(runtime);
            assert cache != null : "Runtime given has not initialized JSON::Ext";
            return cache;
        }
    }

    public boolean encodingsSupported() {
        return utf8 != null;
    }

    public RubyEncoding getEncoding(ThreadContext context, String name) {
        synchronized (encodings) {
            RubyEncoding encoding = encodings.get(name);
            if (encoding == null) {
                Ruby runtime = context.getRuntime();
                encoding = (RubyEncoding)RubyEncoding.find(context,
                        runtime.getEncoding(), runtime.newString(name));
                encodings.put(name, encoding);
            }
            return encoding;
        }
    }
}

