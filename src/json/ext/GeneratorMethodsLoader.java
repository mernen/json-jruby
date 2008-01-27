package json.ext;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.ByteList;

class GeneratorMethodsLoader {
	private final static String
		E_CIRCULAR_DATA_STRUCTURE_CLASS = "JSON::CircularDataStructure",
		E_NESTING_ERROR_CLASS = "JSON::NestingError";

//	private Ruby runtime;
	RubyModule parentModule;

	private abstract static class ToJsonCallback implements Callback {
		public Arity getArity() {
			return Arity.OPTIONAL;
		}

		/**
		 * Safe GeneratorState type-checking
		 * @param vState The unknown parameter you received
		 * @return The same parameter given, assured to be a GeneratorState
		 */
		protected GeneratorState getState(IRubyObject vState) {
			if (!(vState instanceof GeneratorState)) {
				RubyModule generatorState = vState.getRuntime().getClassFromPath("JSON::Ext::Generator::State");
				throw vState.getRuntime().newTypeError(vState, (RubyClass)generatorState);
			}
			return (GeneratorState)vState;
		}

		protected void checkMaxNesting(GeneratorState state, int depth) {
			int currentNesting = 1 + depth;
			if (state.getMaxNesting() != 0 && currentNesting > state.getMaxNesting()) {
				RubyModule eNestingError = state.getRuntime().getClassFromPath(E_NESTING_ERROR_CLASS);
				throw new RaiseException(state.getRuntime(), (RubyClass)eNestingError,
					"nesting of " + currentNesting + " is too deep", false);
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
		setMethod("Object", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return stringToJson.execute(recv.asString(), args, block);
			}
		});

		setMethod("Array", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject vSelf, IRubyObject[] args, Block block) {
				vSelf.checkArrayType();
				RubyArray self = (RubyArray)vSelf;
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
						IRubyObject elementStr = element.callMethod(runtime.getCurrentContext(), "to_json");
						result.append(elementStr);
					}
					result.cat((byte)']');
				}
				else {
					result = transform(self, state, depth);
				}
				result.infectBy(vSelf);
				return result;
			}

			private RubyString transform(RubyArray self, IRubyObject vState, IRubyObject vDepth) {
				Ruby runtime = self.getRuntime();
				ThreadContext context = runtime.getCurrentContext();
				RubyString result = runtime.newString();
				int depth = vDepth.isNil() ? 0 : RubyNumeric.fix2int(vDepth);
				GeneratorState state = getState(vState);

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
							throw new RaiseException(self.getRuntime(),
								(RubyClass)self.getRuntime().getClassFromPath(E_CIRCULAR_DATA_STRUCTURE_CLASS),
								"circular data structures not supported!", false);
						}
						result.infectBy(element);
						if (firstItem) {
							firstItem = false;
						}
						else {
							result.cat(delim);
						}
						result.cat(shift);
						IRubyObject elemJson = element.callMethod(context, "to_json",
							new IRubyObject[] {state, RubyNumeric.int2fix(runtime, depth + 1)});
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
						IRubyObject elemJson = element.callMethod(context, "to_json",
							new IRubyObject[] {state, RubyNumeric.int2fix(runtime, depth + 1)});
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

		// XXX this method does the same as Object#to_json, only possibly slower
		/*
		setMethod("Integer", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.callMethod(recv.getRuntime().getCurrentContext(), "to_s");
			}
		});
		*/

		setMethod("TrueClass", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.getRuntime().newString("true");
			}
		});

		setMethod("FalseClass", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.getRuntime().newString("false");
			}
		});

		setMethod("NilClass", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return recv.getRuntime().newString("null");
			}
		});
	}

	private void setMethod(String moduleName, Callback method) {
		setMethod(moduleName, "to_json", method);
	}

	private void setMethod(String moduleName, String methodName, Callback method) {
		parentModule.defineModuleUnder(moduleName).defineMethod(methodName, method);
	}
}
