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
import org.jruby.RubyFixnum;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * A class that populates the
 * <code>Json::Ext::Generator::GeneratorMethods</code> module.
 * 
 * @author mernen
 */
class GeneratorMethods {
    /**
     * Populates the given module with all modules and their methods
     * @param info
     * @param generatorMethodsModule The module to populate
     * (normally <code>JSON::Generator::GeneratorMethods</code>)
     */
    static void populate(RuntimeInfo info, RubyModule module) {
        defineMethods(module, "Array",      RbArray.class);
        defineMethods(module, "FalseClass", RbFalse.class);
        defineMethods(module, "Float",      RbFloat.class);
        defineMethods(module, "Hash",       RbHash.class);
        defineMethods(module, "Integer",    RbInteger.class);
        defineMethods(module, "NilClass",   RbNil.class);
        defineMethods(module, "Object",     RbObject.class);
        defineMethods(module, "String",     RbString.class);
        defineMethods(module, "TrueClass",  RbTrue.class);

        info.stringExtendModule = module.defineModuleUnder("String")
                                            .defineModuleUnder("Extend");
        info.stringExtendModule.defineAnnotatedMethods(StringExtend.class);
    }

    /**
     * Convenience method for defining methods on a submodule.
     * @param parentModule
     * @param submoduleName
     * @param klass
     */
    private static void defineMethods(RubyModule parentModule,
            String submoduleName, Class klass) {
        RubyModule submodule = parentModule.defineModuleUnder(submoduleName);
        submodule.defineAnnotatedMethods(klass);
    }



    private static GeneratorState getState(ThreadContext context, IRubyObject[] args) {
        if (args.length == 0) return GeneratorState.newInstance(context);
        return GeneratorState.fromState(context, args[0]);
    }



    public static class RbHash {
        /**
         * <code>{@link RubyHash Hash}#to_json(state = nil, depth = 0)</code>
         *
         * <p>Returns a JSON string containing a JSON object, that is unparsed
         * from this Hash instance.
         * <p><code>state</code> is a {@link GeneratorState JSON::State}
         * object, that can also be used to configure the produced JSON string
         * output further.
         * <p><code>depth</code> is used to find the nesting depth, to indent
         * accordingly.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            RubyHash self = Utils.ensureHash(vSelf);

            GeneratorState state = getState(context, args);
            int depth = args.length > 1 ? RubyNumeric.fix2int(args[1]) : 0;

            state.checkMaxNesting(context, depth + 1);
            return transform(context, self, state, depth);
        }

        private static RubyString transform(final ThreadContext context,
                RubyHash self, final GeneratorState state, int depth) {
            Ruby runtime = context.getRuntime();
            final ByteList objectNl = state.getObjectNl();
            final byte[] indent = Utils.repeat(state.getIndent(), depth + 1);
            final ByteList spaceBefore = state.getSpaceBefore();
            final ByteList space = state.getSpace();
            final RubyFixnum subDepth = runtime.newFixnum(depth + 1);

            // Basic estimative, just to get things started
            final int preSize =
                    2 + self.size() * (12 + indent.length + spaceBefore.length()
                                       + space.length());
            // we know the ByteList won't get shared, so it's safe to work
            // directly on it
            final ByteList out = new ByteList(Math.max(preSize, 0));
            final RubyString result = runtime.newString(out);
            result.infectBy(self);

            out.append((byte)'{');
            out.append(objectNl);
            self.visitAll(new RubyHash.Visitor() {
                private boolean firstPair = true;

                @Override
                public void visit(IRubyObject key, IRubyObject value) {
                    if (firstPair) {
                        firstPair = false;
                    } else {
                        out.append((byte)',');
                        out.append(objectNl);
                    }
                    if (objectNl.length() != 0) out.append(indent);

                    RubyString keyJson = Utils.toJson(context, key.asString(),
                            state, subDepth);
                    out.append(keyJson.getByteList());
                    result.infectBy(keyJson);
                    out.append(spaceBefore);
                    out.append((byte)':');
                    out.append(space);

                    RubyString valueJson = Utils.toJson(context, value, state,
                            subDepth);
                    out.append(valueJson.getByteList());
                    result.infectBy(valueJson);
                }
            });
            if (objectNl.length() != 0) {
                out.append(objectNl);
                if (indent.length != 0) {
                    for (int i = 0; i < depth; i++) {
                        out.append(indent);
                    }
                }
            }
            out.append((byte)'}');

            return result;
        }
    };

    public static class RbArray {
        /**
         * <code>{@link RubyArray Array}#to_json(state = nil, depth = 0)</code>
         *
         * <p>Returns a JSON string containing a JSON array, that is unparsed
         * from this Array instance.
         * <p><code>state</code> is a {@link GeneratorState JSON::State}
         * object, that can also be used to configure the produced JSON string
         * output further.
         * <p><code>depth</code> is used to find the nesting depth, to indent
         * accordingly.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            RubyArray self = Utils.ensureArray(vSelf);
            GeneratorState state = getState(context, args);
            int depth = args.length > 1 ? RubyNumeric.fix2int(args[1]) : 0;

            state.checkMaxNesting(context, depth + 1);
            return transform(context, self, state, depth);
        }

        private static RubyString transform(ThreadContext context,
                RubyArray self, GeneratorState state, int depth) {
            Ruby runtime = context.getRuntime();
            ByteList indentUnit = state.getIndent();
            byte[] shift = Utils.repeat(indentUnit, depth + 1);

            ByteList arrayNl = state.getArrayNl();
            byte[] delim = new byte[1 + arrayNl.length()];
            delim[0] = ',';
            System.arraycopy(arrayNl.unsafeBytes(), arrayNl.begin(), delim, 1,
                    arrayNl.length());

            // Basic estimative, doesn't take much into account
            int preSize = 2 + self.size() * (4 + shift.length + delim.length);
            ByteList out = new ByteList(Math.max(preSize, 0));
            RubyString result = runtime.newString(out);
            result.infectBy(self);

            out.append((byte)'[');
            out.append(arrayNl);
            boolean firstItem = true;
            for (int i = 0, t = self.getLength(); i < t; i++) {
                IRubyObject element = self.eltInternal(i);
                result.infectBy(element);
                if (firstItem) {
                    firstItem = false;
                } else {
                    out.append(delim);
                }
                out.append(shift);
                RubyString elemJson = Utils.toJson(context, element, state,
                        RubyNumeric.int2fix(runtime, depth + 1));
                out.append(elemJson.getByteList());
                result.infectBy(elemJson);
            }

            if (arrayNl.length() != 0) {
                out.append(arrayNl);
                out.append(shift, 0, depth * indentUnit.length());
            }

            out.append((byte)']');

            return result;
        }
    };

    public static class RbInteger {
        /**
         * <code>{@link RubyInteger Integer}#to_json(*)</code>
         *
         * <p>Returns a JSON string representation for this Integer number.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject self, IRubyObject[] args) {
            return Utils.ensureString(self.callMethod(context, "to_s"));
        }
    };

    public static class RbFloat {
        /**
         * <code>{@link RubyFloat Float}#to_json(state = nil, *)</code>
         *
         * <p>Returns a JSON string representation for this Float number.
         * <p><code>state</code> is a {@link GeneratorState JSON::State}
         * object, that can also be used to configure the produced JSON string
         * output further.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            double value = RubyFloat.num2dbl(vSelf);

            if ((Double.isInfinite(value) || Double.isNaN(value)) &&
                    (args.length == 0 || !getState(context, args).allowNaN())) {
                throw Utils.newException(context, Utils.M_GENERATOR_ERROR,
                        vSelf + " not allowed in JSON");
            }
            return vSelf.asString();
        }
    };

    public static class RbString {
        /**
         * <code>{@link RubyString String}#to_json(*)</code>
         *
         * <p>Returns a JSON string representation for this String.
         * <p>The string must be encoded in UTF-8. All non-ASCII characters
         * will be escaped as <code>\\u????</code> escape sequences.
         * Characters outside the Basic Multilingual Plane range are encoded
         * as a pair of surrogates.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            Ruby runtime = context.getRuntime();
            RuntimeInfo info = RuntimeInfo.forRuntime(runtime);
            boolean asciiOnly = args.length > 0 && getState(context, args).asciiOnly();
            char[] chars = decodeString(context, info, Utils.ensureString(vSelf));
            // For most apps, the vast majority of strings will be plain simple
            // ASCII strings with no characters that need escaping. So, we'll
            // preallocate just enough space for the entire string plus opening
            // and closing quotes
            ByteList out = new ByteList(2 + chars.length);

            final byte[] escapeSequence = new byte[] { '\\', 0 };
            out.append((byte)'"');
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
                        out.append((byte)c);
                    } else if (asciiOnly || c < 0x20) {
                        out.append(Utils.escapeUnicode(c));
                    } else if (Character.isHighSurrogate(c)) {
                        // reconstruct characters outside of BMP if
                        // surrogates are found
                        if (chars.length <= i + 1) {
                            // incomplete surrogate pair
                            throw illegalUTF8(context);
                        }
                        char nextChar = chars[++i];
                        if (!Character.isLowSurrogate(nextChar)) {
                            // high surrogate without low surrogate
                            throw illegalUTF8(context);
                        }

                        long fullCode = Character.toCodePoint(c, nextChar);
                        out.append(Utils.getUTF8Bytes(fullCode));
                    } else if (Character.isLowSurrogate(c)) {
                        // low surrogate without high surrogate
                        throw illegalUTF8(context);
                    } else {
                        out.append(Utils.getUTF8Bytes(c));
                    }
                    continue charLoop;
                }
                out.append(escapeSequence);
            }
            out.append((byte)'"');
            return runtime.newString(out);
        }

        private static RaiseException illegalUTF8(ThreadContext context) {
            throw Utils.newException(context, Utils.M_GENERATOR_ERROR,
                    "source sequence is illegal/malformed utf-8");
        }

        private static char[] decodeString(ThreadContext context,
                RuntimeInfo info, RubyString string) {
            if (info.encodingsSupported() &&
                    string.encoding(context) != info.utf8) {
                string = (RubyString)string.encode(context, info.utf8);
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
                System.arraycopy(buffer.array(), buffer.position(), result, 0,
                                 result.length);
                return result;
            } catch (CharacterCodingException e) {
                throw Utils.newException(context, Utils.M_GENERATOR_ERROR,
                    "source sequence is illegal/malformed utf-8");
            }
        }

        /**
         * <code>{@link RubyString String}#to_json_raw(*)</code>
         *
         * <p>This method creates a JSON text from the result of a call to
         * {@link #to_json_raw_object} of this String.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json_raw(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            IRubyObject obj = to_json_raw_object(context, vSelf, args);
            return RbHash.to_json(context, obj, args);
        }

        /**
         * <code>{@link RubyString String}#to_json_raw_object(*)</code>
         *
         * <p>This method creates a raw object Hash, that can be nested into
         * other data structures and will be unparsed as a raw string. This
         * method should be used if you want to convert raw strings to JSON
         * instead of UTF-8 strings, e.g. binary data.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json_raw_object(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            RubyString self = Utils.ensureString(vSelf);
            Ruby runtime = context.getRuntime();
            RubyHash result = RubyHash.newHash(runtime);

            IRubyObject createId = RuntimeInfo.forRuntime(runtime)
                    .jsonModule.callMethod(context, "create_id");
            result.op_aset(context, createId, vSelf.getMetaClass().to_s());

            ByteList bl = self.getByteList();
            byte[] uBytes = bl.unsafeBytes();
            RubyArray array = runtime.newArray(bl.length());
            for (int i = bl.begin(), t = bl.begin() + bl.length(); i < t; i++) {
                array.store(i, runtime.newFixnum(uBytes[i] & 0xff));
            }

            result.op_aset(context, runtime.newString("raw"), array);
            return result;
        }

        @JRubyMethod(required=1, module=true)
        public static IRubyObject included(ThreadContext context,
                IRubyObject vSelf, IRubyObject module) {
            RuntimeInfo info = RuntimeInfo.forRuntime(context.getRuntime());
            return module.callMethod(context, "extend", info.stringExtendModule);
        }
    };

    public static class StringExtend {
        /**
         * <code>{@link RubyString String}#json_create(o)</code>
         *
         * <p>Raw Strings are JSON Objects (the raw bytes are stored in an
         * array for the key "raw"). The Ruby String can be created by this
         * module method.
         */
        @JRubyMethod(required=1)
        public static IRubyObject json_create(ThreadContext context,
                IRubyObject vSelf, IRubyObject vHash) {
            Ruby runtime = context.getRuntime();
            RubyHash o = vHash.convertToHash();
            IRubyObject rawData = o.fastARef(runtime.newString("raw"));
            if (rawData == null) {
                throw runtime.newArgumentError("\"raw\" value not defined "
                                               + "for encoded String");
            }
            RubyArray ary = Utils.ensureArray(rawData);
            byte[] bytes = new byte[ary.getLength()];
            for (int i = 0, t = ary.getLength(); i < t; i++) {
                IRubyObject element = ary.eltInternal(i);
                if (element instanceof RubyFixnum) {
                    bytes[i] = (byte)RubyNumeric.fix2long(element);
                } else {
                    throw runtime.newTypeError(element, runtime.getFixnum());
                }
            }
            return runtime.newString(new ByteList(bytes, false));
        }
    };

    protected static ByteList buildKw(String keyword) {
        return new ByteList(ByteList.plain(keyword));
    }

    protected static RubyString generateKw(ThreadContext context,
            ByteList keyword) {
        return RubyString.newStringShared(context.getRuntime(), keyword);
    }

    public static class RbTrue {
        private static ByteList keyword = buildKw("true");

        /**
         * <code>true.to_json(*)</code>
         *
         * <p>Returns a JSON string for <code>true</code>: <code>"true"</code>.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            return generateKw(context, keyword);
        }
    }

    public static class RbFalse {
        private static ByteList keyword = buildKw("false");

        /**
         * <code>false.to_json(*)</code>
         *
         * <p>Returns a JSON string for <code>false</code>: <code>"false"</code>.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            return generateKw(context, keyword);
        }
    }

    public static class RbNil {
        private static ByteList keyword = buildKw("null");

        /**
         * <code>nil.to_json(*)</code>
         *
         * <p>Returns a JSON string for <code>nil</code>: <code>"null"</code>.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args) {
            return generateKw(context, keyword);
        }
    }

    public static class RbObject {
        /**
         * <code>{@link RubyObject Object}#to_json(*)</code>
         *
         * <p>Converts this object to a string (calling <code>#to_s</code>),
         * converts it to a JSON string, and returns the result.
         * This is a fallback, if no special method <code>#to_json</code> was
         * defined for some object.
         */
        @JRubyMethod(rest=true)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject self, IRubyObject[] args) {
            return RbString.to_json(context, self.asString(), args);
        }
    };
}
