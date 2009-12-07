/*
 * This code is copyrighted work by Daniel Luz <dev at mernen dot com>.
 *
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.util.ByteList;

/**
 * A class specialized in reading UTF-8 ByteLists.
 */
abstract class ByteListReader {
    protected final ThreadContext context;
    protected final ByteList src;
    private final int srcLength;
    protected int pos = 0;

    protected ByteListReader(ThreadContext context, ByteList src) {
        this.context = context;
        this.src = src;
        this.srcLength = src.length();
    }

    protected boolean hasNext() {
        return pos < srcLength;
    }

    protected char next() {
        pos++;
        return src.charAt(pos);
    }

    /**
     * Reads an UTF-8 character from the input and returns its code point,
     * while advancing the input position.
     *
     * <p>Raises a GeneratorError if an invalid byte is found.
     *
     * @param head The first byte (which was already read)
     */
    protected int readUtf8Char(char head) {
        if (head <= 0x7f) return head;
        if (head <= 0xbf) throw invalidUtf8(); // tail byte with no head
        if (head <= 0xdf) {
            // 0b110xxxxx
            ensureMin(1);
            return ((head  & 0x1f) << 6)
                   | nextPart();
        }
        if (head <= 0xef) {
            // 0b1110xxxx
            ensureMin(2);
            return ((head & 0x0f) << 12)
                   | (nextPart()  << 6)
                   | nextPart();
        }
        if (head <= 0xf7) {
            // 0b11110xxx
            ensureMin(3);
            int cp = ((head & 0x07) << 18)
                     | (nextPart()  << 12)
                     | (nextPart()  << 6)
                     | nextPart();
            if (!Character.isValidCodePoint(cp)) throw invalidUtf8();
            return cp;
        }
        // 0b11111xxx?
        throw invalidUtf8();
    }

    /**
     * Throws a GeneratorError if the input list doesn't have at least this
     * many bytes left.
     */
    protected void ensureMin(int n) {
        if (pos + n >= srcLength) throw invalidUtf8();
    }

    /**
     * Reads the next byte of a multi-byte UTF-8 character and returns its
     * contents (lower 6 bits).
     *
     * <p>Throws a GeneratorError if the byte is not a valid tail.
     */
    private int nextPart() {
        char c = next();
        // tail bytes must be 0b10xxxxxx
        if ((c & 0xc0) != 0x80) throw invalidUtf8();
        return c & 0x3f;
    }

    protected abstract RaiseException invalidUtf8();
}
