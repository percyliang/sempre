#!/usr/bin/env ruby

# Input: src
# Output: module-classes.txt

# For each module (e.g., core, freebase), we compute the list of classes
# associated with that class.

out_path = 'module-classes.txt'
out = open(out_path, 'w')
items = []
core_packages = ['test']  # Packages in core which are not their own modules
modules = {}
Dir['src/**/*.java'].each { |path|
  class_name = path.sub(/^src\//, '').gsub(/\//, '.').gsub(/\.java$/, '')
  module_name = path.sub(/^.*sempre\//, '').split(/\//)[0]
  module_name = 'core' if module_name =~ /\.java$/ || core_packages.index(module_name)
  modules[module_name] = true
  items << (module_name + " " + class_name)
}
items.sort!
out.puts items
out.close
puts "Wrote modules with #{items.size} files to #{out_path}: #{modules.keys.sort.join(' ')}"
