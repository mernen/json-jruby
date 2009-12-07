package json.ext;

import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.util.ByteList;

/**
 * An encoder that reads from the given UTF-8 ByteList and outputs its
 * representation to another ByteList. The source string is fully checked
 * for UTF-8 validity, and throws a GeneratorError if any problem is found.
 */
final class StringEncoder extends ByteListReader {
    private final ByteList out;
    private final boolean asciiOnly;
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
        super(context, src);
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

    @Override
    protected RaiseException invalidUtf8() {
         return Utils.newException(context, Utils.M_GENERATOR_ERROR,
                 "source sequence is illegal/malformed utf-8");
    }
}
