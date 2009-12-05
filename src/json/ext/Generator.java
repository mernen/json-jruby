/*
 * This code is copyrighted work by Daniel Luz <dev at mernen dot com>.
 * 
 * Distributed under the Ruby and GPLv2 licenses; see COPYING and GPL files
 * for details.
 */
package json.ext;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyInteger;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

public final class Generator {
    private Generator() {
        throw new RuntimeException();
    }

    /**
     * Encodes the given object as a JSON string, using the given handler.
     */
    static <T extends IRubyObject> RubyString
            generateJson(ThreadContext context, T object,
                         Handler<? super T> handler, IRubyObject[] args) {
        Session session = new Session(context, args.length > 0 ? args[0]
                                                               : null);
        int depth = args.length > 1 ? RubyInteger.fix2int(args[1]) : 0;
        return session.infect(handler.generateNew(session, object, depth));
    }

    /**
     * Encodes the given object as a JSON string, using the appropriate
     * handler if one is found or calling #to_json if not.
     */
    public static <T extends IRubyObject> RubyString
            generateJson(ThreadContext context, T object,
                         GeneratorState config, int depth) {
        Session session = new Session(context, config);
        Handler<? super T> handler = getHandlerFor(object);
        return handler.generateNew(session, object, depth);
    }

    /**
     * Returns the best serialization handler for the given object.
     */
    // Java's generics can't handle this satisfactorily, so I'll just leave
    // the best I could get and ignore the warnings
    @SuppressWarnings("unchecked")
    private static <T extends IRubyObject>
            Handler<? super T> getHandlerFor(T object) {
        if (object instanceof RubyHash)    return (Handler)HASH_HANDLER;
        if (object instanceof RubyArray)   return (Handler)ARRAY_HANDLER;
        if (object instanceof RubyString)  return (Handler)STRING_HANDLER;
        if (object instanceof RubyInteger) return (Handler)INTEGER_HANDLER;
        if (object.isNil())                return (Handler)NIL_HANDLER;
        if (object instanceof RubyBoolean) {
            return (Handler)(object.isTrue() ? TRUE_HANDLER : FALSE_HANDLER);
        }
        if (object instanceof RubyFloat)   return (Handler)FLOAT_HANDLER;
        return GENERIC_HANDLER;
    }


    /* Generator context */

    /**
     * A class that concentrates all the information that is shared by
     * generators working on a single session.
     * 
     * <p>A session is defined as the process of serializing a single root
     * object; any handler directly called by container handlers (arrays and
     * hashes/objects) shares this object with its caller.
     * 
     * <p>Note that anything called indirectly (via {@link GENERIC_HANDLER})
     * won't be part of the session.
     */
    static class Session {
        private final ThreadContext context;
        private GeneratorState state;
        private IRubyObject possibleState;
        private RuntimeInfo info;

        private boolean tainted = false;
        private boolean untrusted = false;

        Session(ThreadContext context, GeneratorState state) {
            this.context = context;
            this.state = state;
        }

        Session(ThreadContext context, IRubyObject possibleState) {
            this.context = context;
            this.possibleState = possibleState == null || possibleState.isNil()
                    ? null : possibleState;
        }

        public ThreadContext getContext() {
            return context;
        }

        public Ruby getRuntime() {
            return context.getRuntime();
        }

        public GeneratorState getState() {
            if (state == null) {
                state = GeneratorState.fromState(context, getInfo(), possibleState);
            }
            return state;
        }

        public RuntimeInfo getInfo() {
            if (info == null) info = RuntimeInfo.forRuntime(getRuntime());
            return info;
        }

        public void infectBy(IRubyObject object) {
            if (object.isTaint()) tainted = true;
            if (object.isUntrusted()) untrusted = true;
        }

        public <T extends IRubyObject> T infect(T object) {
            if (tainted) object.setTaint(true);
            if (untrusted) object.setUntrusted(true);
            return object;
        }
    }


    /* Handler base classes */

    private static abstract class Handler<T extends IRubyObject> {
        /**
         * Returns an estimative of how much space the serialization of the
         * given object will take. Used for allocating enough buffer space
         * before invoking other methods.
         */
        int guessSize(Session session, T object, int depth) {
            return 4;
        }

        RubyString generateNew(Session session, T object, int depth) {
            ByteList buffer = new ByteList(guessSize(session, object, depth));
            generate(session, object, buffer, depth);
            return RubyString.newString(session.getRuntime(), buffer);
        }

        abstract void generate(Session session, T object, ByteList buffer,
                               int depth);
    }

    /**
     * A handler that returns a fixed keyword regardless of the passed object.
     */
    private static class KeywordHandler<T extends IRubyObject>
            extends Handler<T> {
        private final ByteList keyword;

        private KeywordHandler(String keyword) {
            this.keyword = new ByteList(ByteList.plain(keyword), false);
        }

        @Override
        int guessSize(Session session, T object, int depth) {
            return keyword.length();
        }

        @Override
        RubyString generateNew(Session session, T object, int depth) {
            return RubyString.newStringShared(session.getRuntime(), keyword);
        }

        @Override
        void generate(Session session, T object, ByteList buffer, int depth) {
            buffer.append(keyword);
        }
    }


    /* Handlers */

    static final Handler<RubyInteger> INTEGER_HANDLER =
        new Handler<RubyInteger>() {
            @Override
            void generate(Session session, RubyInteger object, ByteList buffer,
                          int depth) {
                buffer.append(((RubyString)object.to_s()).getByteList());
            }
        };

    static final Handler<RubyFloat> FLOAT_HANDLER =
        new Handler<RubyFloat>() {
            @Override
            void generate(Session session, RubyFloat object, ByteList buffer,
                          int depth) {
                double value = RubyFloat.num2dbl(object);

                if (Double.isInfinite(value) || Double.isNaN(value)) {
                    if (!session.getState().allowNaN()) {
                        throw Utils.newException(session.getContext(),
                                Utils.M_GENERATOR_ERROR,
                                object + " not allowed in JSON");
                    }
                }
                buffer.append(((RubyString)object.to_s()).getByteList());
            }
        };

    static final Handler<RubyArray> ARRAY_HANDLER =
        new Handler<RubyArray>() {
            @Override
            int guessSize(Session session, RubyArray object, int depth) {
                GeneratorState state = session.getState();
                int perItem =
                    4                                           // prealloc
                    + (depth + 1) * state.getIndent().length()  // indent
                    + 1 + state.getArrayNl().length();          // ',' arrayNl
                return 2 + object.size() * perItem;
            }

            @Override
            void generate(Session session, RubyArray object, ByteList buffer,
                          int depth) {
                GeneratorState state = session.getState();
                state.checkMaxNesting(session.getContext(), depth + 1);

                ByteList indentUnit = state.getIndent();
                byte[] shift = Utils.repeat(indentUnit, depth + 1);

                ByteList arrayNl = state.getArrayNl();
                byte[] delim = new byte[1 + arrayNl.length()];
                delim[0] = ',';
                System.arraycopy(arrayNl.unsafeBytes(), arrayNl.begin(), delim, 1,
                        arrayNl.length());

                session.infectBy(object);

                buffer.append((byte)'[');
                buffer.append(arrayNl);
                boolean firstItem = true;
                for (int i = 0, t = object.getLength(); i < t; i++) {
                    IRubyObject element = object.eltInternal(i);
                    session.infectBy(element);
                    if (firstItem) {
                        firstItem = false;
                    } else {
                        buffer.append(delim);
                    }
                    buffer.append(shift);
                    Handler<IRubyObject> handler = getHandlerFor(element);
                    handler.generate(session, element, buffer, depth + 1);
                }

                if (arrayNl.length() != 0) {
                    buffer.append(arrayNl);
                    buffer.append(shift, 0, depth * indentUnit.length());
                }

                buffer.append((byte)']');
            }
        };

    static final Handler<RubyHash> HASH_HANDLER =
        new Handler<RubyHash>() {
            @Override
            int guessSize(Session session, RubyHash object, int depth) {
                GeneratorState state = session.getState();
                int perItem =
                    12    // key, colon, comma
                    + (depth + 1) * state.getIndent().length()
                    + state.getSpaceBefore().length()
                    + state.getSpace().length();
                return 2 + object.size() * perItem;
            }

            @Override
            void generate(final Session session, RubyHash object,
                          final ByteList buffer, final int depth) {
                final GeneratorState state = session.getState();
                state.checkMaxNesting(session.getContext(), depth + 1);

                final ByteList objectNl = state.getObjectNl();
                final byte[] indent = Utils.repeat(state.getIndent(), depth + 1);
                final ByteList spaceBefore = state.getSpaceBefore();
                final ByteList space = state.getSpace();

                buffer.append((byte)'{');
                buffer.append(objectNl);
                object.visitAll(new RubyHash.Visitor() {
                    private boolean firstPair = true;

                    @Override
                    public void visit(IRubyObject key, IRubyObject value) {
                        if (firstPair) {
                            firstPair = false;
                        } else {
                            buffer.append((byte)',');
                            buffer.append(objectNl);
                        }
                        if (objectNl.length() != 0) buffer.append(indent);

                        STRING_HANDLER.generate(session, key.asString(),
                                                buffer, depth + 1);
                        session.infectBy(key);

                        buffer.append(spaceBefore);
                        buffer.append((byte)':');
                        buffer.append(space);

                        Handler<IRubyObject> valueHandler = getHandlerFor(value);
                        valueHandler.generate(session, value, buffer, depth + 1);
                        session.infectBy(value);
                    }
                });
                if (objectNl.length() != 0) {
                    buffer.append(objectNl);
                    if (indent.length != 0) {
                        for (int i = 0; i < depth; i++) {
                            buffer.append(indent);
                        }
                    }
                }
                buffer.append((byte)'}');
            }
        };

    static final Handler<RubyString> STRING_HANDLER =
        new Handler<RubyString>() {
            @Override
            int guessSize(Session session, RubyString object, int depth) {
                // for most applications, most strings will be just a set of
                // printable ASCII characters without any escaping, so let's
                // just allocate enough space for that + the quotes
                return 2 + object.getByteList().length();
            }

            @Override
            void generate(Session session, RubyString object, ByteList buffer,
                          int depth) {
                boolean asciiOnly = session.getState().asciiOnly();
                char[] chars = decodeString(session, object);
                final byte[] escapeSequence = new byte[] { '\\', 0 };
                buffer.append((byte)'"');

                charLoop:
                for (int i = 0; i < chars.length; i++) {
                    char c = chars[i];
                    switch (c) {
                    case '"':
                    case '\\':
                        escapeSequence[1] = (byte)c;
                        break;
                    case '\n':
                        escapeSequence[1] = 'n';
                        break;
                    case '\r':
                        escapeSequence[1] = 'r';
                        break;
                    case '\t':
                        escapeSequence[1] = 't';
                        break;
                    case '\f':
                        escapeSequence[1] = 'f';
                        break;
                    case '\b':
                        escapeSequence[1] = 'b';
                        break;
                    default:
                        if (c >= 0x20 && c <= 0x7f) {
                            buffer.append((byte)c);
                        } else if (asciiOnly || c < 0x20) {
                            buffer.append(Utils.escapeUnicode(c));
                        } else if (Character.isHighSurrogate(c)) {
                            // reconstruct characters outside of BMP if
                            // surrogates are found
                            if (chars.length <= i + 1) {
                                // incomplete surrogate pair
                                throw illegalUTF8(session);
                            }
                            char nextChar = chars[++i];
                            if (!Character.isLowSurrogate(nextChar)) {
                                // high surrogate without low surrogate
                                throw illegalUTF8(session);
                            }

                            long fullCode = Character.toCodePoint(c, nextChar);
                            buffer.append(Utils.getUTF8Bytes(fullCode));
                        } else if (Character.isLowSurrogate(c)) {
                            // low surrogate without high surrogate
                            throw illegalUTF8(session);
                        } else {
                            buffer.append(Utils.getUTF8Bytes(c));
                        }
                        continue charLoop;
                    }
                    buffer.append(escapeSequence);
                }
                buffer.append((byte)'"');
            }

            private char[] decodeString(Session session, RubyString string) {
                RuntimeInfo info = session.getInfo();
                if (info.encodingsSupported() &&
                        string.encoding(session.getContext()) != info.utf8) {
                    string = (RubyString)string.encode(session.getContext(), info.utf8);
                }

                ByteList byteList = string.getByteList();
                try { // attempt to interpret string as UTF-8
                    CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
                    decoder.onMalformedInput(CodingErrorAction.REPORT);
                    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                    ByteBuffer byteBuffer =
                        ByteBuffer.wrap(byteList.unsafeBytes(), byteList.begin(),
                                        byteList.length());
                    CharBuffer buffer = decoder.decode(byteBuffer);
                    char[] result = new char[buffer.length()];
                    System.arraycopy(buffer.array(), buffer.position(), result,
                                     0, result.length);
                    return result;
                } catch (CharacterCodingException e) {
                    throw Utils.newException(session.getContext(),
                            Utils.M_GENERATOR_ERROR,
                            "source sequence is illegal/malformed utf-8");
                }
            }

            private RaiseException illegalUTF8(Session session) {
                throw Utils.newException(session.getContext(),
                        Utils.M_GENERATOR_ERROR,
                        "source sequence is illegal/malformed utf-8");
            }
        };

    static final Handler<RubyBoolean> TRUE_HANDLER =
        new KeywordHandler<RubyBoolean>("true");
    static final Handler<RubyBoolean> FALSE_HANDLER =
        new KeywordHandler<RubyBoolean>("false");
    static final Handler<IRubyObject> NIL_HANDLER =
        new KeywordHandler<IRubyObject>("null");

    /**
     * The default handler (<code>Object#to_json</code>): coerces the object
     * to string using <code>#to_s</code>, and serializes that string.
     */
    static final Handler<IRubyObject> OBJECT_HANDLER =
        new Handler<IRubyObject>() {
            @Override
            RubyString generateNew(Session session, IRubyObject object,
                                   int depth) {
                RubyString str = object.asString();
                return STRING_HANDLER.generateNew(session, str, depth);
            }

            @Override
            void generate(Session session, IRubyObject object, ByteList buffer,
                          int depth) {
                RubyString str = object.asString();
                STRING_HANDLER.generate(session, str, buffer, depth);
            }
        };

    /**
     * A handler that simply calls <code>#to_json(state, depth)</code> on the
     * given object.
     */
    static final Handler<IRubyObject> GENERIC_HANDLER =
        new Handler<IRubyObject>() {
            @Override
            RubyString generateNew(Session session, IRubyObject object,
                                   int depth) {
                RubyNumeric vDepth =
                    RubyNumeric.int2fix(session.getRuntime(), depth);
                IRubyObject result =
                    object.callMethod(session.getContext(), "to_json",
                          new IRubyObject[] {session.getState(), vDepth});
                if (result instanceof RubyString) return (RubyString)result;
                throw session.getRuntime().newTypeError("to_json must return a String");
            }

            @Override
            void generate(Session session, IRubyObject object, ByteList buffer,
                          int depth) {
                RubyString result = generateNew(session, object, depth);
                buffer.append(result.getByteList());
            }
        };
}
