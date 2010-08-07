require "rake/gempackagetask"

spec = Gem::Specification.new do |s|
  s.name = "json-jruby"
  s.version = File.read("VERSION").chomp
  s.summary = "JSON implementation for JRuby (merged into the json gem)"
  s.description = "A JSON implementation as a JRuby extension. You may now use the json gem directly."
  s.author = "Daniel Luz"
  s.email = "dev+ruby@mernen.com"
  s.homepage = "http://json-jruby.rubyforge.org/"
  s.platform = "java"
  s.rubyforge_project = "json-jruby"

  s.files = []

  s.add_dependency "json", "= #{s.version}"
end

Rake::GemPackageTask.new spec do |pkg|
  pkg.package_files = []
end
