package json.ext;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.builtin.IRubyObject;

abstract class Utils {
	/**
	 * Convenience method for looking up items on a {@link RubyHash Hash}
	 * with a {@link RubySymbol Symbol} key
	 * @param hash The Hash to look up at
	 * @param key The Symbol name to look up for
	 * @return The item in the {@link RubyHash Hash}, or the Hash's
	 *         {@link RubyHash#default_value_get(IRubyObject[]) default} if not found
	 */
	static IRubyObject getSymItem(RubyHash hash, String key) {
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
	 * the value to {@link RubyString String} otherwise
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
	 * Safe {@link GeneratorState} type-checking
	 * @param vState The value to be checked
	 * @return The same parameter given, assured to be a GeneratorState
	 */
	static GeneratorState asState(IRubyObject vState) {
		if (vState instanceof GeneratorState) {
			return (GeneratorState)vState;
		}
		RubyModule generatorState = vState.getRuntime().getClassFromPath("JSON::Ext::Generator::State");
		assert generatorState.getJavaClass() == GeneratorState.class;
		throw vState.getRuntime().newTypeError(vState, (RubyClass)generatorState);
	}

	static RaiseException newException(Ruby runtime, String className, String message) {
		return new RaiseException(runtime, (RubyClass)runtime.getClassFromPath("JSON::" + className),
		                          message, false);
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
