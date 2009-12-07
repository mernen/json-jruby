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
 * A decoder that reads a JSON-encoded string from the given UTF-8 ByteList and
 * returns its decoded form on a new ByteList. The source string is fully
 * checked for UTF-8 validity, and throws a GeneratorError if any problem is
 * found.
 */
final class StringDecoder extends ByteListReader {
    private ByteList out;
    private int quoteStart = -1;
    /**
     * Stores the offset of the high surrogate when reading a surrogate pair,
     * or -1 when not.
     */
    private int surrogatePairStart = -1;

    private final byte[] aux = new byte[4];

    StringDecoder(ThreadContext context) {
        super(context);
    }

    ByteList decode(ByteList src, int start, int end) {
        init(src, start, end);
        out = new ByteList(end - start);
        while (hasNext()) {
            handleChar(readUtf8Char());
        }
        quoteStop(pos);
        return out;
    }

    private void handleChar(int c) {
        if (c == '\\') {
            quoteStop(charStart);
            handleEscapeSequence();
        } else if (Character.isHighSurrogate((char)c)) {
            quoteStop(charStart);
            handleLowSurrogate((char)c);
        } else if (Character.isLowSurrogate((char)c)) {
            // low surrogate with no high surrogate
            throw invalidUtf8();
        } else {
            if (quoteStart == -1) quoteStart = charStart;
        }
    }

    private void handleEscapeSequence() {
        ensureMin(1);
        int c = readUtf8Char();
        switch (c) {
        case 'b':
            out.append('\b');
            break;
        case 'f':
            out.append('\f');
            break;
        case 'n':
            out.append('\n');
            break;
        case 'r':
            out.append('\r');
            break;
        case 't':
            out.append('\t');
            break;
        case 'u':
            ensureMin(4);
            int cp = readHex();
            if (Character.isHighSurrogate((char)cp)) {
                handleLowSurrogate((char)cp);
            } else if (Character.isLowSurrogate((char)cp)) {
                // low surrogate with no high surrogate
                throw invalidUtf8();
            } else {
                writeUtf8Char(cp);
            }
            break;
        default: // '\\', '"', '/'...
            quoteStart = charStart;
        }
    }

    private void handleLowSurrogate(char highSurrogate) {
        surrogatePairStart = charStart;
        ensureMin(1);
        int lowSurrogate = readUtf8Char();

        if (lowSurrogate == '\\') {
            ensureMin(5);
            if (readUtf8Char() != 'u') throw invalidUtf8();
            lowSurrogate = readHex();
        }

        if (Character.isLowSurrogate((char)lowSurrogate)) {
            writeUtf8Char(Character.toCodePoint(highSurrogate,
                                                (char)lowSurrogate));
            surrogatePairStart = -1;
        } else {
            throw invalidUtf8();
        }
    }

    private void writeUtf8Char(int codePoint) {
        if (codePoint < 0x80) {
            out.append(codePoint);
        } else if (codePoint < 0x800) {
            aux[0] = (byte)(0xc0 | (codePoint >>> 6));
            aux[1] = tailByte(codePoint & 0x3f);
            out.append(aux, 0, 2);
        } else if (codePoint < 0x10000) {
            aux[0] = (byte)(0xe0 | (codePoint >>> 12));
            aux[1] = tailByte(codePoint >>> 6);
            aux[2] = tailByte(codePoint);
            out.append(aux, 0, 3);
        } else {
            aux[0] = (byte)(0xf0 | codePoint >>> 18);
            aux[1] = tailByte(codePoint >>> 12);
            aux[2] = tailByte(codePoint >>> 6);
            aux[3] = tailByte(codePoint);
            out.append(aux, 0, 4);
        }
    }

    private byte tailByte(int value) {
        return (byte)(0x80 | (value & 0x3f));
    }

    /**
     * When in a sequence of characters that can be copied directly,
     * interrupts the sequence and copies it to the output buffer.
     */
    private void quoteStop(int endPos) {
        if (quoteStart != -1) {
            out.append(src, quoteStart, endPos - quoteStart);
            quoteStart = -1;
        }
    }

    /**
     * Reads a 4-digit unsigned hexadecimal number from the source.
     */
    private int readHex() {
        int numberStart = pos;
        int result = 0;
        int length = 4;
        for (int i = 0; i < length; i++) {
            int digit = readUtf8Char();
            int digitValue;
            if (digit >= '0' && digit <= '9') {
                digitValue = digit - '0';
            } else if (digit >= 'a' && digit <= 'f') {
                digitValue = 10 + digit - 'a';
            } else if (digit >= 'A' && digit <= 'F') {
                digitValue = 10 + digit - 'A';
            } else {
                throw new NumberFormatException("Invalid base 16 number "
                        + src.subSequence(numberStart, numberStart + length));
            }
            result = result * 16 + digitValue;
        }
        return result;
    }

    @Override
    protected RaiseException invalidUtf8() {
        ByteList message = new ByteList(
                ByteList.plain("partial character in source, " +
                               "but hit end near "));
        int start = surrogatePairStart != -1 ? surrogatePairStart : charStart;
        message.append(src, start, srcEnd - start);
        return Utils.newException(context, Utils.M_PARSER_ERROR,
                                  context.getRuntime().newString(message));
    }
}
