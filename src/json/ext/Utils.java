/*
 * This code is copyrighted work by Daniel Luz <dev at mernen dot com>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyString;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * Library of miscellaneous utility functions
 * 
 * @author mernen
 */
final class Utils {
    public static final String M_GENERATOR_ERROR = "GeneratorError";
    public static final String M_NESTING_ERROR = "NestingError";
    public static final String M_PARSER_ERROR = "ParserError";

    private Utils() {
        throw new RuntimeException();
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
        if (object instanceof RubyArray) return (RubyArray)object;
        Ruby runtime = object.getRuntime();
        throw runtime.newTypeError(object, runtime.getArray());
    }

    static RaiseException newException(ThreadContext context, String className,
                                       String message) {
        Ruby runtime = context.getRuntime();
        return newException(RuntimeInfo.forRuntime(runtime), context,
                            className, runtime.newString(message));
    }

    static RaiseException newException(RuntimeInfo info, ThreadContext context,
                                       String className, String message) {
        return newException(info, context, className,
                            context.getRuntime().newString(message));
    }

    static RaiseException newException(RuntimeInfo info, ThreadContext context,
                                       String className, RubyString message) {
        RubyClass klazz = info.jsonModule.getClass(className);
        RubyException excptn =
            (RubyException)klazz.newInstance(context,
                new IRubyObject[] {message}, Block.NULL_BLOCK);
        return new RaiseException(excptn);
    }

    /**
     * Invokes <code>to_json</code> on the given object and ensures
     * it returns a RubyString
     * @param object The object to convert to JSON
     * @param args Parameters to pass to the method call
     * @return The {@link RubyString String} containing the
     *         JSON representation of the object
     */
    static RubyString toJson(ThreadContext context, IRubyObject object,
                             IRubyObject... args) {
        Ruby runtime = context.getRuntime();
        IRubyObject result = object.callMethod(context, "to_json", args);
        if (result instanceof RubyString) return (RubyString)result;
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
        return repeat(a, 0, a.length, n);
    }

    static byte[] repeat(ByteList a, int n) {
        return repeat(a.unsafeBytes(), a.begin(), a.length(), n);
    }

    static byte[] repeat(byte[] a, int begin, int length, int n) {
        if (length == 0) return ByteList.NULL_ARRAY;
        int resultLen = length * n;
        byte[] result = new byte[resultLen];
        for (int pos = 0; pos < resultLen; pos += length) {
            System.arraycopy(a, begin, result, pos, length);
        }
        return result;
    }

    private static final byte[] HEX =
            new byte[] {'0', '1', '2', '3', '4', '5', '6', '7',
                        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    static byte[] escapeUnicode(char c) {
        return new byte[] {
            '\\', 'u', HEX[(c >>> 12) & 0xf], HEX[(c >>> 8) & 0xf],
                       HEX[(c >>>  4) & 0xf], HEX[c & 0xf]};
    }

    /**
     * Converts a code point into an UTF-8 representation.
     * @param code The character code point
     * @return An array containing the UTF-8 bytes for the given code point
     */
    static byte[] getUTF8Bytes(long code) {
        if (code < 0x80) {
            return new byte[] {(byte)code};
        }
        if (code < 0x800) {
            return new byte[] {(byte)(0xc0 | code >>> 6),
                               (byte)(0x80 | code & 0x3f)};
        }
        if (code < 0x10000) {
            return new byte[] {(byte)(0xe0 | code >>> 12),
                               (byte)(0x80 | code >>> 6 & 0x3f),
                               (byte)(0x80 | code & 0x3f)};
        }
        return new byte[] {(byte)(0xf0 | code >>> 18),
                           (byte)(0x80 | code >>> 12 & 0x3f),
                           (byte)(0x80 | code >>> 6 & 0x3f),
                           (byte)(0x80 | code & 0x3f)};
    }

    /**
     * Efficiently reads an unsigned hexadecimal number straight from a
     * ByteList, without allocating additional objects.
     */
    static int parseHex(ByteList bl, int start, int length) {
        int result = 0;
        for (int i = start, countdown = length; countdown > 0; i++, countdown--) {
            char digit = bl.charAt(i);
            int digitValue;
            if (digit >= '0' && digit <= '9') {
                digitValue = digit - '0';
            }
            else if (digit >= 'a' && digit <= 'f') {
                digitValue = 10 + digit - 'a';
            }
            else if (digit >= 'A' && digit <= 'F') {
                digitValue = 10 + digit - 'A';
            }
            else {
                throw new NumberFormatException("Invalid base 16 number "
                        + bl.subSequence(start, start + length));
            }
            result = result * 16 + digitValue;
        }
        return result;
    }
}
