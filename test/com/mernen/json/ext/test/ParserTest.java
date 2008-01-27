/**
 * 
 */
package com.mernen.json.ext.test;

import json.ext.Init;
import json.ext.Parser;

import org.jruby.Ruby;
import org.junit.BeforeClass;
import org.junit.Test;


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
	 * Test method for {@link json.ext.Parser#parse()}.
	 */
	@Test
	public void testParse() {
		Parser parser = (Parser)vm.evalScriptlet("JSON::Ext::Parser.new('  [\"hello\"] ')");
		parser.parse();
	}
}
