#! /usr/bin/env jruby
require "rubygems"

spec = Gem::Specification.new do |s|
  s.name = "json-jruby"
  s.version = File.read("VERSION").chomp
  s.author = "Daniel Luz"
  s.email = "mernen+rubyforge@gmail.com"
  s.homepage = "http://rubyforge.org/projects/json-jruby/"
  s.platform = Gem::Platform::CURRENT
  s.summary = "A JSON implementation as a JRuby extension"
  s.rubyforge_project = "json-jruby"

  s.files = Dir["{docs,lib,tests}/**/*"]
  s.test_files << "tests/runner.rb"
end

if $0 == __FILE__
  Gem::Builder.new(spec).build
end
