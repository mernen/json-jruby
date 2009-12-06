package json.ext;

import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.util.ByteList;

/**
 * An encoder that reads from the given UTF-8 ByteList and outputs its
 * representation to another ByteList. The source string is fully checked
 * for UTF-8 validity, and throws a GeneratorError if any problem is found.
 */
final class StringEncoder {
    private final ThreadContext context;
    private final ByteList src;
    private final int srcLength;
    private final ByteList out;
    private final boolean asciiOnly;
    private int pos = 0;
    /**
     * When a character that can be copied straight into the output is found,
     * its index is stored on this variable, and copying is delayed until
     * the sequence of characters that can be copied ends.
     * 
     * <p>The variable stores -1 when not in a plain sequence.
     */
    private int quoteStart = -1;

    /**
     * Escaped characters will reuse this array, to avoid new allocations
     * or appending them byte-by-byte
     */
    private final byte[] aux =
        new byte[] {/* First unicode character */
                    '\\', 'u', 0, 0, 0, 0,
                    /* Second unicode character (for surrogate pairs) */
                    '\\', 'u', 0, 0, 0, 0,
                    /* "\X" characters */
                    '\\', 0};
    // offsets on the array above
    private static final int ESCAPE_UNI1_OFFSET = 0;
    private static final int ESCAPE_UNI2_OFFSET = ESCAPE_UNI1_OFFSET + 6;
    private static final int ESCAPE_CHAR_OFFSET = ESCAPE_UNI2_OFFSET + 6;
    /** Array used for code point decomposition in surrogates */
    private final char[] utf16 = new char[2];

    private static final byte[] HEX =
            new byte[] {'0', '1', '2', '3', '4', '5', '6', '7',
                        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    static void encode(ThreadContext context, ByteList src, ByteList out,
                       boolean asciiOnly) {
        new StringEncoder(context, src, out, asciiOnly).encode();
    }

    private StringEncoder(ThreadContext context, ByteList src, ByteList out,
                          boolean asciiOnly) {
        this.context = context;
        this.src = src;
        this.srcLength = src.length();
        this.out = out;
        this.asciiOnly = asciiOnly;
    }

    private void encode() {
        out.append('"');
        while (hasNext()) {
            handleChar(src.charAt(pos));
            pos++;
        }
        quoteStop();
        out.append('"');
    }

    private boolean hasNext() {
        return pos < srcLength;
    }

    private char next() {
        pos++;
        return src.charAt(pos);
    }

    private void handleChar(char c) {
        switch (c) {
        case '"':
        case '\\':
            escapeChar(c);
            break;
        case '\n':
            escapeChar('n');
            break;
        case '\r':
            escapeChar('r');
            break;
        case '\t':
            escapeChar('t');
            break;
        case '\f':
            escapeChar('f');
            break;
        case '\b':
            escapeChar('b');
            break;
        default:
            if (c >= 0x20 && c <= 0x7f) {
                if (quoteStart == -1) quoteStart = pos;
            } else if (c >= 0x80 && !asciiOnly) {
                if (quoteStart == -1) quoteStart = pos;
                // read anyway, to skip its other bytes and ensure it's valid
                readUtf8Char(c);
            } else {
                quoteStop();
                int codePoint = readUtf8Char(c);
                escapeUtf8Char(codePoint);
            }
        }
    }

    private void escapeChar(char c) {
        quoteStop();
        aux[ESCAPE_CHAR_OFFSET + 1] = (byte)c;
        out.append(aux, ESCAPE_CHAR_OFFSET, 2);
    }

    /**
     * When in a sequence of characters that can be copied directly,
     * interrupts the sequence and copies it to the output buffer.
     */
    private void quoteStop() {
        if (quoteStart != -1) {
            out.append(src, quoteStart, pos - quoteStart);
            quoteStart = -1;
        }
    }

    /**
     * Reads an UTF-8 character from the input and returns its code point,
     * while advancing the input position.
     * 
     * <p>Raises a GeneratorError if an invalid byte is found.
     * 
     * @param head The first byte (which was already read)
     */
    private int readUtf8Char(char head) {
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
    private void ensureMin(int n) {
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

    private void escapeUtf8Char(int codePoint) {
        int numChars = Character.toChars(codePoint, utf16, 0);
        escapeCodeUnit(utf16[0], ESCAPE_UNI1_OFFSET + 2);
        if (numChars > 1) escapeCodeUnit(utf16[1], ESCAPE_UNI2_OFFSET + 2);
        out.append(aux, ESCAPE_UNI1_OFFSET, 6 * numChars);
    }

    private void escapeCodeUnit(char c, int auxOffset) {
        for (int i = 0; i < 4; i++) {
            aux[auxOffset + i] = HEX[(c >>> (12 - 4 * i)) & 0xf];
        }
    }

    private RaiseException invalidUtf8() {
         return Utils.newException(context, Utils.M_GENERATOR_ERROR,
                 "source sequence is illegal/malformed utf-8");
    }
}
