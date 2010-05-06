#! /usr/bin/env jruby
require "rubygems"

spec = Gem::Specification.new do |s|
  s.name = "json-jruby"
  s.version = File.read("VERSION").chomp
  s.summary = "JSON implementation for JRuby"
  s.description = "A JSON implementation as a JRuby extension."
  s.author = "Daniel Luz"
  s.email = "dev+ruby@mernen.com"
  s.homepage = "http://json-jruby.rubyforge.org/"
  s.platform = Gem::Platform::CURRENT
  s.rubyforge_project = "json-jruby"

  s.files = Dir["{docs,lib,tests}/**/*"]
end

if $0 == __FILE__
  Gem::Builder.new(spec).build
end
