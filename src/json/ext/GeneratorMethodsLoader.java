/*
 * This code is copyrighted work by Daniel Luz <@gmail.com: mernen>.
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

	private static Callback objectToJson = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
			return stringToJson.execute(recv.asString(), args, block);
		}
	};

	private static Callback hashToJson = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
			RubyHash self = vSelf.convertToHash();
			Ruby runtime = self.getRuntime();
			args = Arity.scanArgs(runtime, args, 0, 2);
			IRubyObject vState = args[0];

			if (vState.isNil()) {
				final RubyString result = runtime.newString();
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
			else {
				GeneratorState state = Utils.ensureState(vState);
				IRubyObject vDepth = args[1].isNil() ? runtime.newFixnum(0) : args[1];
				RubyString result;

				if (!args[1].isNil()) {
					state.checkMaxNesting(RubyNumeric.fix2int(args[1]) + 1);
				}
				if (state.checkCircular()) {
					if (state.hasSeen(self)) {
						throw Utils.newException(runtime, Utils.M_CIRCULAR_DATA_STRUCTURE,
							"circular data structures not supported!");
					}
					state.remember(self);
					result = transform(self, state, vDepth);
					state.forget(self);
				}
				else {
					result = transform(self, state, vDepth);
				}

				return result;
			}
		}

		private RubyString transform(RubyHash self, final GeneratorState state, IRubyObject vDepth) {
			Ruby runtime = self.getRuntime();
			final int depth = RubyNumeric.fix2int(vDepth);
			final RubyString result = runtime.newString();

			final byte[] objectNl = state.object_nl_get().getBytes();
			final byte[] indent = Utils.repeat(state.indent_get().getBytes(), depth + 1);
			final byte[] spaceBefore = state.space_before_get().getBytes();
			final byte[] space = state.space_get().getBytes();
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
					if (objectNl.length != 0) {
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
			if (objectNl.length != 0) {
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

	private static Callback arrayToJson = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
			RubyArray self = Utils.ensureArray(vSelf);
			Ruby runtime = self.getRuntime();
			args = Arity.scanArgs(runtime, args, 0, 2);
			IRubyObject state = args[0];
			IRubyObject depth = args[1];
			RubyString result;

			if (state.isNil()) {
				result = runtime.newString();
				result.cat((byte)'[');
				result.infectBy(vSelf);
				for (int i = 0, t = self.getLength(); i < t; i++) {
					IRubyObject element = self.entry(i);
					result.infectBy(element);
					if (i > 0) {
						result.cat((byte)',');
					}
					IRubyObject elementStr = Utils.toJson(element);
					result.append(elementStr);
				}
				result.cat((byte)']');
			}
			else {
				result = transform(self, Utils.ensureState(state), depth);
			}
			result.infectBy(vSelf);
			return result;
		}

		private RubyString transform(RubyArray self, GeneratorState state, IRubyObject vDepth) {
			Ruby runtime = self.getRuntime();
			RubyString result = runtime.newString();
			int depth = vDepth.isNil() ? 0 : RubyNumeric.fix2int(vDepth);

	        byte[] indentUnit = state.indent_get().getBytes();
			byte[] shift = Utils.repeat(indentUnit, depth + 1);

	        result.infectBy(self);

			byte[] arrayNl = state.array_nl_get().getBytes();
			byte[] delim = new byte[1 + arrayNl.length];
			delim[0] = ',';
			System.arraycopy(arrayNl, 0, delim, 1, arrayNl.length);

			state.checkMaxNesting(depth + 1);
			if (state.checkCircular()) {
				state.remember(self);

				result.cat((byte)'[');
				result.cat(arrayNl);

				boolean firstItem = true;
				for (IRubyObject element : self.toJavaArrayMaybeUnsafe()) {
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
					IRubyObject elemJson = Utils.toJson(element, state, RubyNumeric.int2fix(runtime, depth + 1));
					result.cat(elemJson.convertToString().getByteList());
				}

				if (arrayNl.length != 0) {
					result.cat(arrayNl);
					result.cat(shift, 0, depth * indentUnit.length);
				}

				result.cat((byte)']');

				state.forget(self);
			}
			else {
				result.cat((byte)'[');
				result.cat(arrayNl);
				boolean firstItem = true;
				for (IRubyObject element : self.toJavaArrayMaybeUnsafe()) {
					result.infectBy(element);
					if (firstItem) {
						firstItem = false;
					}
					else {
						result.cat(delim);
					}
					result.cat(shift);
					IRubyObject elemJson = Utils.toJson(element, state, RubyNumeric.int2fix(runtime, depth + 1));
					result.cat(elemJson.convertToString().getByteList());
				}

				if (arrayNl.length != 0) {
					result.cat(arrayNl);
					result.cat(shift, 0, depth * indentUnit.length);
				}

				result.cat((byte)']');
			}

			return result;
		}
	};

	private static Callback integerToJson = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
			return recv.callMethod(recv.getRuntime().getCurrentContext(), "to_s");
		}
	};

	private static Callback floatToJson = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
			double value = RubyFloat.num2dbl(vSelf);

			if (Double.isInfinite(value) || Double.isNaN(value)) {
				GeneratorState state = args.length > 0 ? Utils.ensureState(args[0]) : null;
				if (state == null || state.allowNaN()) {
					// XXX wouldn't it be better to hardcode a representation?
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

	private static Callback stringToJson = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
			// using convertToString as a safety guard measure
			char[] chars = decodeString(self.convertToString());
			RubyString result = self.getRuntime().newString();
			result.modify(chars.length);
			result.cat((byte)'"');
			for (char c : chars) {
				if (c == '"') {
					result.cat(new byte[] {'\\', '"'});
				}
				else if (c == '\\') {
					result.cat(new byte[] {'\\', '\\'});
				}
				else if (c == '/') {
					result.cat(new byte[] {'\\', '/'});
				}
				else if (c >= 0x20 && c <= 0x7f) {
					result.cat((byte)c);
				}
				else if (c == '\n') {
					result.cat(new byte[] {'\\', 'n'});
				}
				else if (c == '\r') {
					result.cat(new byte[] {'\\', 'r'});
				}
				else if (c == '\t') {
					result.cat(new byte[] {'\\', 't'});
				}
				else if (c == '\f') {
					result.cat(new byte[] {'\\', 'f'});
				}
				else if (c == '\b') {
					result.cat(new byte[] {'\\', 'b'});
				}
				else {
					result.cat(escapeUnicode(c));
				}
			}
			result.cat((byte)'"');
			return result;
		}

		private char[] decodeString(RubyString string) {
			byte[] bytes = string.getBytes();
			try { // attempt to interpret string as UTF-8
				CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
				decoder.onMalformedInput(CodingErrorAction.REPORT);
				decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
				CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
				char[] result = new char[buffer.length()];
				System.arraycopy(buffer.array(), buffer.position(), result, 0, result.length);
				return result;
			} catch (CharacterCodingException e) {
				// Florian's library strictly only interprets UTF-8
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

		private byte[] escapeUnicode(char c) {
			final byte[] hex = new byte[] {'0', '1', '2', '3', '4', '5', '6', '7',
			                               '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
			return new byte[] {
				'\\', 'u', hex[(c >>> 12) & 0xf], hex[(c >>> 8) & 0xf],
				           hex[(c >>>  4) & 0xf], hex[c & 0xf]};
		}
	};

	private static Callback stringToJsonRaw = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
			IRubyObject obj = stringToJsonRawObject.execute(vSelf, args, block);
			return hashToJson.execute(obj, args, block);
		}
	};

	private static Callback stringToJsonRawObject = new OptionalArgsCallback() {
		public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
			RubyString self = vSelf.convertToString();
			Ruby runtime = self.getRuntime();
			RubyHash result = RubyHash.newHash(runtime);

			IRubyObject createId =
				runtime.getModule("JSON").callMethod(runtime.getCurrentContext(), "create_id");
			result.op_aset(createId, vSelf.getMetaClass().to_s());

			byte[] bytes = self.getBytes();
			RubyArray array = runtime.newArray(bytes.length);
			for (int i = 0; i < bytes.length; i++) {
				array.store(i, runtime.newFixnum(bytes[i] & 0xff));
			}

			result.op_aset(runtime.newString("raw"), array);
			return result;
		}
	};

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
	 * A general class for keyword values
	 * (<code>true</code>, <code>false</code>, <code>null</code>).
	 * Stores its keyword as a shared ByteList for performance.
	 * @author mernen
	 */
	private static class KeywordJsonConverter extends OptionalArgsCallback {
		private final ByteList keyword;

		private KeywordJsonConverter(String keyword) {
			super();
			this.keyword = new ByteList(ByteList.plain(keyword), false);
		}

		public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
			return RubyString.newStringShared(self.getRuntime(), keyword);
		}
	}

	private static Callback trueToJson = new KeywordJsonConverter("true");
	private static Callback falseToJson = new KeywordJsonConverter("false");
	private static Callback nilToJson = new KeywordJsonConverter("null");

	GeneratorMethodsLoader(RubyModule module) {
		this.parentModule = module;
	}

	void load() {
		defineToJson("Object", objectToJson);

		defineToJson("Hash", hashToJson);

		defineToJson("Array", arrayToJson);

		defineToJson("Integer", integerToJson);

		defineToJson("Float", floatToJson);

		defineToJson("String", stringToJson);
		defineMethod("String", "to_json_raw", stringToJsonRaw);
		defineMethod("String", "to_json_raw_object", stringToJsonRawObject);

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
