package json.ext;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

class GeneratorMethodsLoader {
//	private Ruby runtime;
	RubyModule generatorMethods;

	private abstract static class ToJsonCallback implements Callback {
		public Arity getArity() {
			return Arity.OPTIONAL;
		}
	}

	private static Callback stringToJson = new ToJsonCallback() {
		public IRubyObject execute(IRubyObject recv, IRubyObject[] args,
				Block block) {
			// using convertToString as a safety guard measure
			char[] chars = decodeString(recv.convertToString());
			RubyString result = recv.getRuntime().newString();
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

	GeneratorMethodsLoader(Ruby runtime) {
//		this.runtime = runtime;
		generatorMethods = runtime.getClassFromPath("JSON::Ext::Generator::GeneratorMethods");
	}

	void apply() {
		setMethod("Object", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				return stringToJson.execute(recv.asString(), args, block);
			}
		});
		setMethod("Array", new ToJsonCallback() {
			public IRubyObject execute(IRubyObject recv, IRubyObject[] args, Block block) {
				recv.checkStringType();
				RubyArray self = (RubyArray)recv;
				IRubyObject state = args.length > 0 ? args[0] : null;
				IRubyObject depth = args.length > 1 ? args[1] : null;
				RubyString result;

				if (state == null || state.isNil()) {
					result = recv.getRuntime().newString();
					result.cat((byte)'[');
					result.infectBy(recv);
					for (int i = 0, t = self.getLength(); i < t; i++) {
						IRubyObject element = self.entry(i);
						result.infectBy(element);
						if (i > 0) {
							result.cat((byte)',');
						}
						IRubyObject elementStr = element.callMethod(recv.getRuntime().getCurrentContext(), "to_json").checkStringType();
						result.append(elementStr);
					}
					result.cat((byte)']');
				}
				else {
					result = transform(recv, state, depth);
				}
				result.infectBy(recv);
				return result;
			}

			private RubyString transform(IRubyObject recv, IRubyObject state, IRubyObject depth) {
				// TODO
				return recv.getRuntime().newString();
			}
		});
	}

	private void setMethod(String moduleName, Callback method) {
		generatorMethods.defineModuleUnder(moduleName).defineMethod("to_json", method);
	}
}
