/**
 * 
 */
package com.mernen.json.ext.test;

import org.jruby.Ruby;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mernen.json.ext.Init;
import com.mernen.json.ext.Parser;

/**
 * @author mernen
 */
public class ParserTest {
	static Ruby vm;

	@BeforeClass
	public static void setUp() {
		vm = Ruby.newInstance();
		vm.evalScriptlet("$LOAD_PATH << './lib'");
		Init.prepare(vm.newArray());
	}

	/**
	 * Test method for {@link com.mernen.json.ext.Parser#parse()}.
	 */
	@Test
	public void testParse() {
		Parser parser = (Parser)vm.evalScriptlet("JSON::Ext::Parser.new('  [\"hello\"] ')");
		parser.parse();
	}
}
