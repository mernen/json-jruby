/*
 * This code is copyrighted work by Daniel Luz <mernen at gmail dot com>.
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
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.ByteList;

/**
 * A class that populates the <code>Json::Ext::Generator::GeneratorMethods</code>
 * module.
 * 
 * @author mernen
 */
class GeneratorMethodsLoader {
    private final RubyModule parentModule;

    private abstract static class OptionalArgsCallback implements Callback {
        public Arity getArity() {
            return Arity.OPTIONAL;
        }
    }

    /**
     * <code>{@link RubyHash Hash}#to_json(state = nil, depth = 0)</code>
     * 
     * <p>Returns a JSON string containing a JSON object, that is unparsed from
     * this Hash instance.
     * <p><code>state</code> is a {@link GeneratorState JSON::State} object,
     * that can also be used to configure the produced JSON string output further.
     * <p><code>depth</code> is used to find the nesting depth, to indent accordingly.
     */
    private static Callback HASH_TO_JSON = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            RubyHash self = vSelf.convertToHash();
            Ruby runtime = self.getRuntime();
            args = Arity.scanArgs(runtime, args, 0, 2);
            IRubyObject vState = args[0];

            if (vState.isNil()) {
                return simpleTransform(self);
            }
            else {
                GeneratorState state = Utils.ensureState(vState);
                int depth;
                RubyString result;

                if (args[1].isNil()) {
                    depth = 0;
                }
                else {
                    depth = RubyNumeric.fix2int(args[1]);
                    state.checkMaxNesting(depth + 1);
                }
                if (state.checkCircular()) {
                    if (state.hasSeen(self)) {
                        throw Utils.newException(runtime, Utils.M_CIRCULAR_DATA_STRUCTURE,
                            "circular data structures not supported!");
                    }
                    state.remember(self);
                    result = transform(self, state, depth);
                    state.forget(self);
                }
                else {
                    result = transform(self, state, depth);
                }

                return result;
            }
        }

        /**
         * Performs a simple Hash-to-JSON conversion, with no customization.
         * @param hash The Hash to process
         * @return The JSON representation of the Hash
         */
        private RubyString simpleTransform(RubyHash self) {
            int preSize = 2 + Math.max(self.size() * 8, 0);
            final RubyString result = self.getRuntime().newString(new ByteList(preSize));
            result.cat((byte)'{');
            self.visitAll(new RubyHash.Visitor() {
                private boolean firstPair = true;
                @Override
                public void visit(IRubyObject key, IRubyObject value) {
                    // XXX key == Qundef???
                    if (firstPair) {
                        firstPair = false;
                    }
                    else {
                        result.cat((byte)',');
                    }

                    RubyString jsonKey = Utils.toJson(key.asString());
                    result.cat(((RubyString)jsonKey).getByteList());
                    result.infectBy(jsonKey);
                    result.cat((byte)':');

                    RubyString jsonValue = Utils.toJson(value);
                    result.cat(jsonValue.getByteList());
                    result.infectBy(jsonValue);
                }
            });
            result.cat((byte)'}');
            return result;
        }

        private RubyString transform(RubyHash self, final GeneratorState state, int depth) {
            Ruby runtime = self.getRuntime();
            int preSize = 2 + Math.max(self.size() * 8, 0);
            final RubyString result = runtime.newString(new ByteList(preSize));

            final ByteList objectNl = state.object_nl_get().getByteList();
            final byte[] indent = Utils.repeat(state.indent_get().getByteList(), depth + 1);
            final ByteList spaceBefore = state.space_before_get().getByteList();
            final ByteList space = state.space_get().getByteList();
            final RubyFixnum subDepth = runtime.newFixnum(depth + 1);

            result.cat((byte)'{');
            result.cat(objectNl);
            self.visitAll(new RubyHash.Visitor() {
                private boolean firstPair = true;
                @Override
                public void visit(IRubyObject key, IRubyObject value) {
                    // XXX key == Qundef???
                    if (firstPair) {
                        firstPair = false;
                    }
                    else {
                        result.cat((byte)',');
                        result.cat(objectNl);
                    }
                    if (objectNl.length() != 0) {
                        result.cat(indent);
                    }
                    RubyString keyJson = Utils.toJson(key.asString(), state, subDepth);
                    result.cat(keyJson.getByteList());
                    result.infectBy(keyJson);
                    result.cat(spaceBefore);
                    result.cat((byte)':');
                    result.cat(space);

                    RubyString valueJson = Utils.toJson(value, state, subDepth);
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

    /**
     * <code>{@link RubyArray Array}#to_json(state = nil, depth = 0)</code>
     * 
     * <p>Returns a JSON string containing a JSON array, that is unparsed from
     * this Array instance.
     * <p><code>state</code> is a {@link GeneratorState JSON::State} object,
     * that can also be used to configure the produced JSON string output further.
     * <p><code>depth</code> is used to find the nesting depth, to indent accordingly.
     */
    private static Callback ARRAY_TO_JSON = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            RubyArray self = Utils.ensureArray(vSelf);
            Ruby runtime = self.getRuntime();
            args = Arity.scanArgs(runtime, args, 0, 2);
            IRubyObject state = args[0];
            IRubyObject vDepth = args[1];
            RubyString result;

            if (state.isNil()) {
                int preSize = 2 + Math.max(self.size() * 4, 0);
                result = runtime.newString(new ByteList(preSize));
                result.cat((byte)'[');
                result.infectBy(vSelf);
                for (int i = 0, t = self.getLength(); i < t; i++) {
                    IRubyObject element = self.eltInternal(i);
                    result.infectBy(element);
                    if (i > 0) {
                        result.cat((byte)',');
                    }
                    RubyString elementStr = Utils.toJson(element);
                    result.append(elementStr);
                }
                result.cat((byte)']');
            }
            else {
                int depth = vDepth.isNil() ? 0 : RubyNumeric.fix2int(vDepth);
                result = transform(self, Utils.ensureState(state), depth);
            }
            result.infectBy(vSelf);
            return result;
        }

        private RubyString transform(RubyArray self, GeneratorState state, int depth) {
            final Ruby runtime = self.getRuntime();
            final int preSize = 2 + Math.max(self.size() * 4, 0);
            final RubyString result = runtime.newString(new ByteList(preSize));

            ByteList indentUnit = state.indent_get().getByteList();
            byte[] shift = Utils.repeat(indentUnit, depth + 1);

            result.infectBy(self);

            ByteList arrayNl = state.array_nl_get().getByteList();
            byte[] delim = new byte[1 + arrayNl.length()];
            delim[0] = ',';
            System.arraycopy(arrayNl.unsafeBytes(), arrayNl.begin(), delim, 1, arrayNl.length());

            state.checkMaxNesting(depth + 1);
            if (state.checkCircular()) {
                state.remember(self);

                result.cat((byte)'[');
                result.cat(arrayNl);

                boolean firstItem = true;
                for (int i = 0, t = self.getLength(); i < t; i++) {
                    IRubyObject element = self.eltInternal(i);
                    if (state.hasSeen(element)) {
                        throw Utils.newException(runtime, Utils.M_CIRCULAR_DATA_STRUCTURE,
                            "circular data structures not supported!");
                    }
                    result.infectBy(element);
                    if (firstItem) {
                        firstItem = false;
                    }
                    else {
                        result.cat(delim);
                    }
                    result.cat(shift);
                    RubyString elemJson = Utils.toJson(element, state, RubyNumeric.int2fix(runtime, depth + 1));
                    result.cat(elemJson.getByteList());
                }

                if (arrayNl.length() != 0) {
                    result.cat(arrayNl);
                    result.cat(shift, 0, depth * indentUnit.length());
                }

                result.cat((byte)']');

                state.forget(self);
            }
            else {
                result.cat((byte)'[');
                result.cat(arrayNl);
                boolean firstItem = true;
                for (int i = 0, t = self.getLength(); i < t; i++) {
                    IRubyObject element = self.eltInternal(i);
                    result.infectBy(element);
                    if (firstItem) {
                        firstItem = false;
                    }
                    else {
                        result.cat(delim);
                    }
                    result.cat(shift);
                    RubyString elemJson = Utils.toJson(element, state, RubyNumeric.int2fix(runtime, depth + 1));
                    result.cat(elemJson.getByteList());
                }

                if (arrayNl.length() != 0) {
                    result.cat(arrayNl);
                    result.cat(shift, 0, depth * indentUnit.length());
                }

                result.cat((byte)']');
            }

            return result;
        }
    };

    /**
     * <code>{@link RubyInteger Integer}#to_json(*)</code>
     * 
     * <p>Returns a JSON string representation for this Integer number.
     */
    private static Callback INTEGER_TO_JSON = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
            return recv.callMethod(recv.getRuntime().getCurrentContext(), "to_s");
        }
    };

    /**
     * <code>{@link RubyFloat Float}#to_json(state = nil, *)</code>
     * 
     * <p>Returns a JSON string representation for this Float number.
     * <p><code>state</code> is a {@link GeneratorState JSON::State} object,
     * that can also be used to configure the produced JSON string output further.
     */
    private static Callback FLOAT_TO_JSON = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            double value = RubyFloat.num2dbl(vSelf);

            if (Double.isInfinite(value) || Double.isNaN(value)) {
                GeneratorState state = args.length > 0 ? Utils.ensureState(args[0]) : null;
                if (state == null || state.allowNaN()) {
                    return vSelf.asString();
                }
                else {
                    throw Utils.newException(vSelf.getRuntime(), Utils.M_GENERATOR_ERROR,
                                             vSelf + " not allowed in JSON");
                }
            }
            else {
                return vSelf.asString();
            }
        }
    };

    /**
     * <code>{@link RubyString String}#to_json(*)</code>
     * 
     * <p>Returns a JSON string representation for this String.
     * <p>The string must be encoded in UTF-8. All non-ASCII characters will be
     * escaped as <code>\\u????</code> escape sequences. Characters outside the
     * Basic Multilingual Plane range are encoded as a pair of surrogates.
     */
    private static Callback STRING_TO_JSON = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            // using convertToString as a safety guard measure
            RubyString self = vSelf.convertToString();
            char[] chars = decodeString(self);
            int preSize = 2 + self.getByteList().length();
            RubyString result = self.getRuntime().newString(new ByteList(preSize));
            result.cat((byte)'"');
            final byte[] escapeSequence = new byte[] { '\\', 0 };
            for (char c : chars) {
                switch (c) {
                    case '"':
                    case '/':
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
                        }
                        else {
                            result.cat(Utils.escapeUnicode(c));
                        }
                        continue;
                }
                result.cat(escapeSequence);
            }
            result.cat((byte)'"');
            return result;
        }

        private char[] decodeString(RubyString string) {
            ByteList byteList = string.getByteList();
            try { // attempt to interpret string as UTF-8
                CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
                decoder.onMalformedInput(CodingErrorAction.REPORT);
                decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                ByteBuffer byteBuffer = ByteBuffer.wrap(byteList.unsafeBytes(), byteList.begin(), byteList.length());
                CharBuffer buffer = decoder.decode(byteBuffer);
                char[] result = new char[buffer.length()];
                System.arraycopy(buffer.array(), buffer.position(), result, 0, result.length);
                return result;
            } catch (CharacterCodingException e) {
                // XXX DISABLED: Florian's library strictly only interprets UTF-8
                /*
                // a very naïve decoder, which just maps bytes
                // XXX is this *really* equivalent to the ISO-8859-1 decoder?
                char[] chars = new char[bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    chars[i] = (char)(bytes[i] & 0xff);
                }
                return chars;
                */
                throw Utils.newException(string.getRuntime(), Utils.M_GENERATOR_ERROR,
                    "source sequence is illegal/malformed");
            }
        }
    };

    /**
     * <code>{@link RubyString String}#to_json_raw(*)</code>
     * 
     * <p>This method creates a JSON text from the result of a call to
     * {@link STRING_TO_JSON_RAW_OBJECT to_json_raw_object} of this String.
     */
    private static Callback STRING_TO_JSON_RAW = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            IRubyObject obj = STRING_TO_JSON_RAW_OBJECT.execute(vSelf, args, block);
            return HASH_TO_JSON.execute(obj, args, block);
        }
    };

    /**
     * <code>{@link RubyString String}#to_json_raw_object(*)</code>
     * 
     * <p>This method creates a raw object Hash, that can be nested into other
     * data structures and will be unparsed as a raw string. This method should
     * be used if you want to convert raw strings to JSON instead of UTF-8
     * strings, e.g. binary data.
     */
    private static Callback STRING_TO_JSON_RAW_OBJECT = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            RubyString self = vSelf.convertToString();
            Ruby runtime = self.getRuntime();
            RubyHash result = RubyHash.newHash(runtime);

            IRubyObject createId =
                runtime.getModule("JSON").callMethod(runtime.getCurrentContext(), "create_id");
            result.op_aset(createId, vSelf.getMetaClass().to_s());

            ByteList bl = self.getByteList();
            byte[] uBytes = bl.unsafeBytes();
            RubyArray array = runtime.newArray(bl.length());
            for (int i = bl.begin(), t = bl.begin() + bl.length(); i < t; i++) {
                array.store(i, runtime.newFixnum(uBytes[i] & 0xff));
            }

            result.op_aset(runtime.newString("raw"), array);
            return result;
        }
    };

    /**
     * <code>{@link RubyString String}#json_create(o)</code>
     * 
     * <p>Raw Strings are JSON Objects (the raw bytes are stored in an array for
     * the key "raw"). The Ruby String can be created by this module method.
     */
    private static Callback stringExtendJsonCreate = new Callback() {
        public Arity getArity() {
            return Arity.ONE_ARGUMENT;
        }

        public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
            Ruby runtime = vSelf.getRuntime();
            RubyHash o = args[0].convertToHash();
            IRubyObject rawData = o.fastARef(runtime.newString("raw"));
            if (rawData == null) {
                throw runtime.newArgumentError("\"raw\" value not defined for encoded String");
            }
            RubyArray ary = Utils.ensureArray(rawData);
            byte[] bytes = new byte[ary.getLength()];
            for (int i = 0, t = ary.getLength(); i < t; i++) {
                IRubyObject element = ary.eltInternal(i);
                if (element instanceof RubyFixnum) {
                    bytes[i] = (byte)RubyNumeric.fix2long(element);
                }
                else {
                    throw runtime.newTypeError(element, runtime.getFixnum());
                }
            }
            return runtime.newString(new ByteList(bytes, false));
        }
    };

    /**
     * A general converter for keyword values
     * (<code>true</code>, <code>false</code>, <code>null</code>).
     * @author mernen
     */
    private static class KeywordJsonConverter extends OptionalArgsCallback {
        // Store keyword as a shared ByteList for performance.
        private final ByteList keyword;

        private KeywordJsonConverter(String keyword) {
            super();
            this.keyword = new ByteList(ByteList.plain(keyword), false);
        }

        public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
            return RubyString.newStringShared(self.getRuntime(), keyword);
        }
    }

    /**
     * <code>true.to_json(*)</code>
     * 
     * <p>Returns a JSON string for <code>true</code>: <code>"true"</code>.
     */
    private static Callback trueToJson = new KeywordJsonConverter("true");
    /**
     * <code>false.to_json(*)</code>
     * 
     * <p>Returns a JSON string for <code>false</code>: <code>"false"</code>.
     */
    private static Callback falseToJson = new KeywordJsonConverter("false");
    /**
     * <code>nil.to_json(*)</code>
     * 
     * <p>Returns a JSON string for <code>nil</code>: <code>"null"</code>.
     */
    private static Callback nilToJson = new KeywordJsonConverter("null");

    /**
     * <code>{@link RubyObject Object}#to_json(*)</code>
     * 
     * <p>Converts this object to a string (calling <code>#to_s</code>),
     * converts it to a JSON string, and returns the result.
     * This is a fallback, if no special method <code>#to_json</code> was
     * defined for some object.
     */
    private static Callback OBJECT_TO_JSON = new OptionalArgsCallback() {
        public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
            return STRING_TO_JSON.execute(recv.asString(), args, block);
        }
    };

    /**
     * Instantiates the RubyModule element.
     * @param generatorMethodsModule The module to populate
     * (normally <code>JSON::Generator::GeneratorMethods</code>)
     */
    GeneratorMethodsLoader(RubyModule generatorMethodsModule) {
        this.parentModule = generatorMethodsModule;
    }

    /**
     * Performs the generation of all submodules and methods.
     */
    void load() {
        defineToJson("Object", OBJECT_TO_JSON);

        defineToJson("Hash", HASH_TO_JSON);

        defineToJson("Array", ARRAY_TO_JSON);

        defineToJson("Integer", INTEGER_TO_JSON);

        defineToJson("Float", FLOAT_TO_JSON);

        defineToJson("String", STRING_TO_JSON);
        defineMethod("String", "to_json_raw", STRING_TO_JSON_RAW);
        defineMethod("String", "to_json_raw_object", STRING_TO_JSON_RAW_OBJECT);

        RubyModule stringModule = parentModule.defineModuleUnder("String");
        final RubyModule stringExtend = stringModule.defineModuleUnder("Extend");
        stringModule.defineModuleFunction("included", new Callback() {
            public Arity getArity() {
                return Arity.ONE_ARGUMENT;
            }

            public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
                ThreadContext context = vSelf.getRuntime().getCurrentContext();
                return args[0].callMethod(context, "extend", stringExtend);
            }
        });
        defineMethod(stringExtend, "json_create", stringExtendJsonCreate);

        defineToJson("TrueClass", trueToJson);
        defineToJson("FalseClass", falseToJson);
        defineToJson("NilClass", nilToJson);
    }

    /**
     * Convenience method for defining "to_json" on a module.
     * @param moduleName
     * @param method
     */
    private void defineToJson(String moduleName, Callback method) {
        defineMethod(moduleName, "to_json", method);
    }

    /**
     * Convenience method for defining arbitrary methods on a module (by name).
     * @param moduleName
     * @param methodName
     * @param method
     */
    private void defineMethod(String moduleName, String methodName, Callback method) {
        defineMethod(parentModule.defineModuleUnder(moduleName), methodName, method);
    }

    /**
     * Convenience methods for defining arbitrary methods on a module (by reference).
     * @param module
     * @param methodName
     * @param method
     */
    private void defineMethod(RubyModule module, String methodName, Callback method) {
        module.defineMethod(methodName, method);
    }
}
