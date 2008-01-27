#! /usr/bin/env jruby
$LOAD_PATH << "./lib"
require "json/ext"

ss = Object.new
<<<<<<< HEAD:test.rb
def ss.to_str; "[111]" end
=======
def ss.to_str; "[[null, 3.2e15],null,[true,false],[[[1.0]]]]" end
>>>>>>> Added support for floating-point numbers:test.rb
p JSON::Ext::Parser.new(ss, {}).parse
