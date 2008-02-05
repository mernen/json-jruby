/*
 * This code is copyrighted work by Daniel Luz <mernen at gmail dot com>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyHash;
import org.jruby.RubyString;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * Library of miscellaneous utility functions
 * 
 * @author mernen
 */
final class Utils {
    public static final String M_CIRCULAR_DATA_STRUCTURE = "CircularDatastructure";
    public static final String M_GENERATOR_ERROR = "GeneratorError";
    public static final String M_NESTING_ERROR = "NestingError";
    public static final String M_PARSER_ERROR = "ParserError";

    private Utils() {
	    throw new RuntimeException();
	}

    /**
     * Convenience method for looking up items on a {@link RubyHash Hash}
     * with a {@link RubySymbol Symbol} key
     * @param hash The Hash to look up at
     * @param key The Symbol name to look up for
     * @return The item in the {@link RubyHash Hash}, or the Hash's
     *         {@link RubyHash#default_value_get(IRubyObject[]) default} if not found
     */
    static IRubyObject getSymItem(RubyHash hash, String key) {
        System.err.println("!!");
        return hash.op_aref(hash.getRuntime().newSymbol(key));
    }

    /**
     * Fast convenience method for looking up items on a {@link RubyHash Hash}
     * with a {@link RubySymbol Symbol} key
     * @param hash The Hash to look up at
     * @param key The Symbol name to look up for
     * @return The item in the {@link RubyHash Hash},
     *         or <code>null</code> if not found
     */
    static IRubyObject fastGetSymItem(RubyHash hash, String key) {
        return hash.fastARef(hash.getRuntime().newSymbol(key));
    }

    /**
     * Looks up for an entry in a {@link RubyHash Hash} with a
     * {@link RubySymbol Symbol} key. If no entry is set for this key or if it
     * evaluates to <code>false</code>, returns null; attempts to coerce
     * the value to {@link RubyString String} otherwise.
     * @param hash The Hash to look up
     * @param key The Symbol name to look up for
     * @return <code>null</code> if the key is not in the Hash or if
     *         its value evaluates to <code>false</code>; its 
     * @throws RaiseException <code>TypeError</code> if the value does not
     *                        evaluate to <code>false</code> and can't be
     *                        converted to string
     */
    static RubyString getSymString(RubyHash hash, String key)
            throws RaiseException {
        IRubyObject value = fastGetSymItem(hash, key);
        return value != null && value.isTrue() ? value.convertToString() : null;
    }

    /**
     * Safe {@link GeneratorState} type-checking.
     * Returns the given object if it is a
     * <code>JSON::Ext::Generator::State</code>, or throws an exception if not.
     * @param object The object to test
     * @return The given object if it is a <code>State</code>
     * @throws RaiseException <code>TypeError</code> if the object is not
     *                        of the expected type
     */
    static GeneratorState ensureState(IRubyObject object) {
        if (object instanceof GeneratorState) {
            return (GeneratorState)object;
        }
        Ruby runtime = object.getRuntime();
        RubyClass generatorState =
            (RubyClass)runtime.getClassFromPath("JSON::Ext::Generator::State");
        assert generatorState.getAllocator() == GeneratorState.ALLOCATOR;
        throw runtime.newTypeError(object, generatorState);
    }

    /**
     * Safe {@link RubyArray} type-checking.
     * Returns the given object if it is an <code>Array</code>,
     * or throws an exception if not.
     * @param object The object to test
     * @return The given object if it is an <code>Array</code>
     * @throws RaiseException <code>TypeError</code> if the object is not
     *                        of the expected type
     */
    static RubyArray ensureArray(IRubyObject object) throws RaiseException {
        if (object instanceof RubyArray) {
            return (RubyArray)object;
        }
        Ruby runtime = object.getRuntime();
        throw runtime.newTypeError(object, runtime.getArray());
    }

    static RaiseException newException(Ruby runtime, String className, String message) {
        RubyClass klazz = runtime.getModule("JSON").getClass(className);
        return new RaiseException(runtime, klazz, message, false);
    }

    /**
     * Invokes <code>to_json</code> on the given object and ensures it
     * returns a RubyString
     * @param object The object to convert to JSON
     * @param args Parameters to pass to the method call
     * @return The {@link RubyString String} containing the
     *         JSON representation of the object
     */
    static RubyString toJson(IRubyObject object, IRubyObject... args) {
        Ruby runtime = object.getRuntime();
        IRubyObject result = object.callMethod(runtime.getCurrentContext(), "to_json", args);
        if (result instanceof RubyString) {
            return (RubyString)result;
        }
        throw runtime.newTypeError("to_json must return a String");
    }

    /**
     * Repeats a sequence of bytes a determined number of times
     * @param a The byte array to repeat
     * @param n The number of times to repeat the sequence
     * @return A new byte array, of length <code>bytes.length * times</code>,
     *         with the given array repeated <code>n</code> times
     */
    static byte[] repeat(byte[] a, int n) {
        int len = a.length;
        int resultLen = len * n;
        byte[] result = new byte[resultLen];
        for (int pos = 0; pos < resultLen; pos += len) {
            System.arraycopy(a, 0, result, pos, len);
        }
        return result;
    }
}
