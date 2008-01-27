#! /usr/bin/env jruby
$LOAD_PATH << "./lib"
require "json/ext"

ss = Object.new
def ss.to_str; "[111]" end
p JSON::Ext::Parser.new(ss, {}).parse
