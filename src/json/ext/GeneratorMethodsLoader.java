package json.ext;

import java.nio.ByteBuffer;
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
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.ByteList;

class GeneratorMethodsLoader {
	private final static String
		E_CIRCULAR_DATA_STRUCTURE_CLASS = "CircularDataStructure",
		E_NESTING_ERROR_CLASS = "NestingError",
		E_GENERATOR_ERROR_CLASS = "GeneratorError";

//	private Ruby runtime;
	RubyModule parentModule;

	private abstract static class ToJsonCallback implements Callback {
		public Arity getArity() {
			return Arity.OPTIONAL;
		}

		protected void checkMaxNesting(GeneratorState state, int depth) {
			int currentNesting = 1 + depth;
			if (state.getMaxNesting() != 0 && currentNesting > state.getMaxNesting()) {
				throw Utils.newException(state.getRuntime(), E_NESTING_ERROR_CLASS,
					"nesting of " + currentNesting + " is too deep");
			}
		}
	}

	private static Callback stringToJson = new ToJsonCallback() {
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
				return decoder.decode(ByteBuffer.wrap(bytes)).array();
			} catch (CharacterCodingException e) {
				// a very naïve decoder, which just maps bytes
				// XXX is this *really* equivalent to the ISO-8859-1 decoder?
				char[] chars = new char[bytes.length];
				for (int i = 0; i < bytes.length; i++) {
					chars[i] = (char)bytes[i];
				}
				return chars;
			}
		}

		private byte[] escapeUnicode(char c) {
			final byte[] hex = new byte[] {'0', '1', '2', '3', '4', '5', '6', '7',
			                               '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
			return new byte[] {
				'\\', 'u', hex[(c >>> 12) & 16], hex[(c >>> 8) & 16],
				           hex[(c >>>  4) & 16], hex[c & 16]};
		}
	};

	GeneratorMethodsLoader(RubyModule module) {
		this.parentModule = module;
	}

	void apply() {
		defineToJson("Object", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return stringToJson.execute(recv.asString(), args, block);
			}
		});

		defineToJson("Hash", new ToJsonCallback() {
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
					GeneratorState state = Utils.asState(vState);
					IRubyObject vDepth = args[1].isNil() ? runtime.newFixnum(0) : args[1];
					RubyString result;

					if (!args[1].isNil()) {
						checkMaxNesting(state, RubyNumeric.fix2int(args[1]));
					}
					if (state.checkCircular()) {
						if (state.hasSeen(self)) {
							throw Utils.newException(runtime, E_CIRCULAR_DATA_STRUCTURE_CLASS,
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
				final int depth = RubyNumeric.fix2int(vDepth) + 1;
				final RubyString result = runtime.newString();

				final byte[] objectNl = state.object_nl_get().getBytes();
				final byte[] indent = Utils.repeat(state.indent_get().getBytes(), depth);
				final byte[] spaceBefore = state.space_before_get().getBytes();
				final byte[] space = state.space_get().getBytes();
				final RubyFixnum subDepth = runtime.newFixnum(depth);

				state.setMemo(result);
				state.setDepth(depth);
				state.setStateFlag(false);
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
						
						RubyString valueJson = Utils.toJson(value.asString(), state, subDepth);
						state.setStateFlag(true);
						result.cat(valueJson.getByteList());
						result.infectBy(valueJson);
						state.setDepth(depth);
						state.setMemo(result);
					}
				});
				if (objectNl.length != 0) {
					result.cat(objectNl);
					if (indent.length != 0) {
						result.modify(result.getByteList().length() + indent.length * depth);
						for (int i = 0; i < depth; i++) {
							result.cat(indent);
						}
					}
				}
				result.cat((byte)'}');

				return result;
			}
		});

		defineToJson("Array", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
				RubyArray self = vSelf.convertToArray();
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
					result = transform(self, Utils.asState(state), depth);
				}
				result.infectBy(vSelf);
				return result;
			}

			private RubyString transform(RubyArray self, GeneratorState state, IRubyObject vDepth) {
				Ruby runtime = self.getRuntime();
				RubyString result = runtime.newString();
				int depth = vDepth.isNil() ? 0 : RubyNumeric.fix2int(vDepth);

				ByteList shift;
		        ByteList indentUnit = state.indent_get().getByteList();

		        if (indentUnit.length() == 0) {
		        	shift = new ByteList(0);
		        }
		        else {
					int n = depth + 1;
			        // overflow check straight from RubyString#op_mul
			        if (n > 0 && Integer.MAX_VALUE / n < indentUnit.length()) {
			            throw runtime.newArgumentError("argument too big");
			        }

			        shift = new ByteList(indentUnit.length() * n);
					for (int i = 0; i < n; i++) {
						shift.append(indentUnit);
					}
		        }

		        result.infectBy(self);

				ByteList arrayNl = state.array_nl_get().getByteList();
				ByteList delim = new ByteList(new byte[] {','});
				if (arrayNl.length() != 0) {
					delim.append(arrayNl);
				}

				checkMaxNesting(state, depth);
				if (state.checkCircular()) {
					state.remember(self);

					result.cat((byte)'[');
					result.cat(arrayNl);

					boolean firstItem = true;
					for (IRubyObject element : self.toJavaArrayMaybeUnsafe()) {
						if (state.hasSeen(element)) {
							throw Utils.newException(runtime, E_CIRCULAR_DATA_STRUCTURE_CLASS,
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

					if (arrayNl.length() != 0) {
						result.cat(arrayNl);
						result.cat(shift.bytes(), 0, depth * indentUnit.length());
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

					if (arrayNl.length() != 0) {
						result.cat(arrayNl);
						result.cat(shift.bytes(), 0, depth * indentUnit.length());
					}

					result.cat((byte)']');
				}

				return result;
			}
		});

		defineToJson("Integer", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.callMethod(recv.getRuntime().getCurrentContext(), "to_s");
			}
		});

		defineToJson("Float", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
				double value = RubyFloat.num2dbl(vSelf);

				if (Double.isInfinite(value) || Double.isNaN(value)) {
					GeneratorState state = args.length > 0 ? Utils.asState(args[0]) : null;
					if (state == null || state.allowNaN()) {
						// XXX wouldn't it be better to hardcode a representation?
						return vSelf.asString();
					}
					else {
						throw Utils.newException(vSelf.getRuntime(), E_GENERATOR_ERROR_CLASS,
						                         vSelf + " not allowed in JSON");
					}
				}
				else {
					return vSelf.asString();
				}
			}
		});

		defineToJson("String", stringToJson);

		defineToJson("TrueClass", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.getRuntime().newString("true");
			}
		});

		defineToJson("FalseClass", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.getRuntime().newString("false");
			}
		});

		defineToJson("NilClass", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.getRuntime().newString("null");
			}
		});
	}

	private void defineToJson(String moduleName, Callback method) {
		defineMethod(moduleName, "to_json", method);
	}

	private void defineMethod(String moduleName, String methodName, Callback method) {
		parentModule.defineModuleUnder(moduleName).defineMethod(methodName, method);
	}
}
