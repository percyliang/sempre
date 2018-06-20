#!/usr/bin/env ruby

# Heuristically find all hard-coded paths in the source code.
# There should be no absolute paths.
Dir["src/**/*.java"].each { |sourcePath|
  IO.foreach(sourcePath) { |line|
    next unless line =~ /"([\w_\/\.]+)"/
    file = $1
    next unless file =~ /^\/[uU]\w+\/\w+/ || file =~ /^lib\//
    if file =~ /^\//
      message = " [BAD: absolute path]"
    elsif not File.exists?(file)
      message = " [BAD: does not exist]"
    else
      message = ""
    end
    puts sourcePath + ": " + file + message
  }
}
