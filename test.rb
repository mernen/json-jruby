#! /usr/bin/env jruby
$LOAD_PATH << "./lib"
require "json/ext"

ss = Object.new
<<<<<<< HEAD:test.rb
<<<<<<< HEAD:test.rb
<<<<<<< HEAD:test.rb
<<<<<<< HEAD:test.rb
<<<<<<< HEAD:test.rb
def ss.to_str; "[111]" end
=======
def ss.to_str; "[[null, 3.2e15],null,[true,false],[[[1.0]]]]" end
>>>>>>> Added support for floating-point numbers:test.rb
=======
def ss.to_str; "[3,3,[null, 3.2e15],null,[true,false],[[[1.0]]]]" end
>>>>>>> Added support for integers:test.rb
=======
def ss.to_str; '["3","\ufeff\n",["",[]],-3,4,5.0e2]' end
>>>>>>> Added string support:test.rb
=======
def ss.to_str; '["3","\ufeff\u0041\n",["",[]],-3,4,5.0e2]' end
>>>>>>> Reimplemented stringUnescape to use Ruby strings and encode \u escapes as UTF-8:test.rb
=======
def ss.to_str; '{"int" : "3","str":"\ufeff\u0041\n","hash":{"":{}},"list":[[[{}]]],"int2":-3,"float":4.1,"float2":5.0e2}' end
>>>>>>> Implemented Object (Hash) support:test.rb
p JSON::Ext::Parser.new(ss, {}).parse
p JSON.parse('[[[[[[[[[[[[[[[[[[[["Too deep"]]]]]]]]]]]]]]]]]]]]')
