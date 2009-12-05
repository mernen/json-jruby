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
import org.jruby.runtime.Block;
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
            RubyHash self = vSelf.convertToHash();

            GeneratorState state = getState(context, args);
            int depth = args.length > 1 ? RubyNumeric.fix2int(args[1]) : 0;

            state.checkMaxNesting(depth + 1);
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
            // Math.max() is just being careful with overflowing
            final int preSize = Math.max(0,
                    2 + self.size() * (12 + indent.length +
                                       spaceBefore.length() + space.length()));
            final RubyString result = runtime.newString(new ByteList(preSize));
            result.infectBy(self);

            result.cat((byte)'{');
            result.cat(objectNl);
            self.visitAll(new RubyHash.Visitor() {
                private boolean firstPair = true;

                @Override
                public void visit(IRubyObject key, IRubyObject value) {
                    if (firstPair) {
                        firstPair = false;
                    } else {
                        result.cat((byte)',');
                        result.cat(objectNl);
                    }
                    if (objectNl.length() != 0) result.cat(indent);

                    RubyString keyJson = Utils.toJson(context, key.asString(),
                            state, subDepth);
                    result.cat(keyJson.getByteList());
                    result.infectBy(keyJson);
                    result.cat(spaceBefore);
                    result.cat((byte)':');
                    result.cat(space);

                    RubyString valueJson = Utils.toJson(context, value, state,
                            subDepth);
                    result.cat(valueJson.getByteList());
                    result.infectBy(valueJson);
                }
            });
            if (objectNl.length() != 0) {
                result.cat(objectNl);
                if (indent.length != 0) {
                    for (int i = 0; i < depth; i++) {
                        result.cat(indent);
                    }
                }
            }
            result.cat((byte)'}');

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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
            RubyArray self = Utils.ensureArray(vSelf);
            GeneratorState state = getState(context, args);
            int depth = args.length > 1 ? RubyNumeric.fix2int(args[1]) : 0;

            state.checkMaxNesting(depth + 1);
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
            // Math.max() is just being careful with overflowing
            int preSize = Math.max(0,
                    2 + self.size() * (4 + shift.length + delim.length));
            final RubyString result = runtime.newString(new ByteList(preSize));
            result.infectBy(self);

            result.cat((byte)'[');
            result.cat(arrayNl);
            boolean firstItem = true;
            for (int i = 0, t = self.getLength(); i < t; i++) {
                IRubyObject element = self.eltInternal(i);
                result.infectBy(element);
                if (firstItem) {
                    firstItem = false;
                } else {
                    result.cat(delim);
                }
                result.cat(shift);
                RubyString elemJson = Utils.toJson(context, element, state,
                        RubyNumeric.int2fix(runtime, depth + 1));
                result.cat(elemJson.getByteList());
                result.infectBy(elemJson);
            }

            if (arrayNl.length() != 0) {
                result.cat(arrayNl);
                result.cat(shift, 0, depth * indentUnit.length());
            }

            result.cat((byte)']');

            return result;
        }
    };

    public static class RbInteger {
        /**
         * <code>{@link RubyInteger Integer}#to_json(*)</code>
         *
         * <p>Returns a JSON string representation for this Integer number.
         */
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject self, IRubyObject[] args, Block block) {
            return self.callMethod(context, "to_s").checkStringType();
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
            Ruby runtime = context.getRuntime();
            RuntimeInfo info = RuntimeInfo.forRuntime(runtime);
            boolean asciiOnly = args.length > 0 && getState(context, args).asciiOnly();
            // using convertToString as a safety guard measure
            char[] chars = decodeString(context, info, vSelf.convertToString());
            // For most apps, the vast majority of strings will be plain simple
            // ASCII strings with no characters that need escaping. So, we'll
            // preallocate just enough space for the entire string plus opening
            // and closing quotes
            int preSize = 2 + chars.length;
            RubyString result = runtime.newString(new ByteList(preSize));
            result.cat((byte)'"');
            final byte[] escapeSequence = new byte[] { '\\', 0 };
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
                        result.cat((byte)c);
                    } else if (asciiOnly || c < 0x20) {
                        result.cat(Utils.escapeUnicode(c));
                    } else if (Character.isHighSurrogate(c)) {
                        // reconstruct characters outside of BMP if
                        // surrogates are found
                        if (chars.length <= i + 1) {
                            // incomplete surrogate pair
                            throw illegalUTF8(context, info);
                        }
                        char nextChar = chars[++i];
                        if (!Character.isLowSurrogate(nextChar)) {
                            // high surrogate without low surrogate
                            throw illegalUTF8(context, info);
                        }

                        long fullCode = Character.toCodePoint(c, nextChar);
                        result.cat(Utils.getUTF8Bytes(fullCode));
                    } else if (Character.isLowSurrogate(c)) {
                        // low surrogate without high surrogate
                        throw illegalUTF8(context, info);
                    } else {
                        result.cat(Utils.getUTF8Bytes(c));
                    }
                    continue charLoop;
                }
                result.cat(escapeSequence);
            }
            result.cat((byte)'"');
            return result;
        }

        private static RaiseException illegalUTF8(ThreadContext context,
                RuntimeInfo info) {
            throw Utils.newException(info, context,
                    Utils.M_GENERATOR_ERROR,
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
                throw Utils.newException(info, context, Utils.M_GENERATOR_ERROR,
                    "source sequence is illegal/malformed utf-8");
            }
        }

        /**
         * <code>{@link RubyString String}#to_json_raw(*)</code>
         *
         * <p>This method creates a JSON text from the result of a call to
         * {@link #to_json_raw_object} of this String.
         */
        @JRubyMethod(optional=2)
        public static IRubyObject to_json_raw(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
            IRubyObject obj = to_json_raw_object(context, vSelf, args, block);
            return RbHash.to_json(context, obj, args, block);
        }

        /**
         * <code>{@link RubyString String}#to_json_raw_object(*)</code>
         *
         * <p>This method creates a raw object Hash, that can be nested into
         * other data structures and will be unparsed as a raw string. This
         * method should be used if you want to convert raw strings to JSON
         * instead of UTF-8 strings, e.g. binary data.
         */
        @JRubyMethod(optional=2)
        public static IRubyObject to_json_raw_object(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
            RubyString self = vSelf.convertToString();
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
                IRubyObject vSelf, IRubyObject module, Block block) {
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
                IRubyObject vSelf, IRubyObject vHash, Block block) {
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject vSelf, IRubyObject[] args, Block block) {
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
        @JRubyMethod(optional=2)
        public static IRubyObject to_json(ThreadContext context,
                IRubyObject self, IRubyObject[] args, Block block) {
            return RbString.to_json(context, self.asString(), args, block);
        }
    };
}
