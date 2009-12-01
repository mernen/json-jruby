package json.ext;

import java.util.Collections;
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
            Collections.synchronizedMap(new WeakHashMap<Ruby, RuntimeInfo>(1));

    public final RubyModule mJson;
    public final RubyClass generatorStateClass;

    public final RubyEncoding utf8;
    public final RubyEncoding ascii8bit;
    // other encodings
    private final Map<String, RubyEncoding> encodings;

    private RuntimeInfo(Ruby runtime) {
        ThreadContext context = runtime.getCurrentContext();

        mJson = runtime.getModule("JSON");
        generatorStateClass = (RubyClass)runtime.getClassFromPath("JSON::Ext::Generator::State");

        RubyClass encodingClass = runtime.getEncoding();
        if (encodingClass == null) {
            utf8 = ascii8bit = null;
            encodings = null;
        }
        else {
            utf8 = (RubyEncoding)RubyEncoding.find(context,
                    encodingClass, runtime.newString("utf-8"));
            ascii8bit = (RubyEncoding)RubyEncoding.find(context,
                    encodingClass, runtime.newString("ascii-8bit"));
            encodings = new HashMap<String, RubyEncoding>();
        }
    }

    public static RuntimeInfo forRuntime(Ruby runtime) {
        RuntimeInfo cache = runtimes.get(runtime);
        if (cache == null) {
            cache = new RuntimeInfo(runtime);
            runtimes.put(runtime, cache);
        }
        return cache;
    }

    public boolean encodingsSupported() {
        return utf8 != null;
    }

    public RubyEncoding getEncoding(ThreadContext context, String name) {
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

