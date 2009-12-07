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

    protected ByteList src;
    private int srcEnd;
    /** Position where the last read character started */
    protected int charStart;
    /** Position of the next character to read */
    protected int pos;

    protected ByteListReader(ThreadContext context) {
        this.context = context;
    }

    protected void init(ByteList src) {
        this.init(src, 0, src.length());
    }

    protected void init(ByteList src, int start, int end) {
        this.src = src;
        this.pos = start;
        this.charStart = start;
        this.srcEnd = end;
    }

    /**
     * Returns whether there are any characters left to be read.
     */
    protected boolean hasNext() {
        return pos < srcEnd;
    }

    /**
     * Returns the next character in the buffer.
     */
    private char next() {
        return src.charAt(pos++);
    }

    /**
     * Reads an UTF-8 character from the input and returns its code point,
     * while advancing the input position.
     *
     * <p>Raises an {@link #invalidUtf8()} exception if an invalid byte
     * is found.
     */
    protected int readUtf8Char() {
        charStart = pos;
        char head = next();
        if (head <= 0x7f) { // 0b0xxxxxxx (ASCII)
            return head;
        }
        if (head <= 0xbf) { // 0b10xxxxxx
            throw invalidUtf8(); // tail byte with no head
        }
        if (head <= 0xdf) { // 0b110xxxxx
            ensureMin(1);
            int cp = ((head  & 0x1f) << 6)
                     | nextPart();
            if (cp < 0x0080) throw invalidUtf8();
            return cp;
        }
        if (head <= 0xef) { // 0b1110xxxx
            ensureMin(2);
            int cp = ((head & 0x0f) << 12)
                     | (nextPart()  << 6)
                     | nextPart();
            if (cp < 0x0800) throw invalidUtf8();
            return cp;
        }
        if (head <= 0xf7) { // 0b11110xxx
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
        if (pos + n > srcEnd) throw invalidUtf8();
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
