require 'json/common'

include Java

module JSON
  # This module holds all the modules/classes that implement JSON's
  # functionality as JSON extensions.
  module Ext
    require 'json/ext/parser'
    require 'json/ext/generator'
    $DEBUG and warn "Using Java extension for JSON."
    JSON.parser = Parser
    JSON.generator = Generator
  end
end
