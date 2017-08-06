#!/usr/bin/env ruby

# Hacky script for automatically fixing style errors.  This script is far from
# perfect and you should manually inspect all changes before making changes.

# Usage:
#   scripts/fix-checkstyle.rb           # Inspect changes
#   scripts/fix-checkstyle.rb mutate    # Apply
#   scripts/fix-checkstyle.rb <files>   # Do to particular files

mutate = ARGV.index('mutate'); ARGV = ARGV.select { |a| a != 'mutate' }

files = ARGV.size > 0 ? ARGV : Dir['src/**/*.java']

files.each { |path|
  in_multi_comment = false
  line_num = 0
  lines = IO.readlines(path).map { |line|
    line_num += 1
    line = line.chomp
    new_line = line + ''

    # Remove trailing whitespace
    new_line.gsub!(/\s+$/, '')

    # Add space after comment
    new_line.gsub!(/ \/\/(\w)/, '// \1')

    # Put space after certain operators
    new_line.gsub!(/ (if|for|while|catch)\(/, ' \1 (')

    # Put space
    new_line.gsub!(/( for \(\w+ \w+): /, '\1 : ')

    # Put space after casts
    new_line.gsub!(/\((\w+)\)([a-z])/, '(\1) \2')

    # Reverse modifier order
    new_line.gsub!(/ final static /, ' static final ')

    in_single_quote = false
    in_double_quote = false
    in_comment = false
    tokens = new_line.split(//)
    (0...tokens.size).each { |i|
      # Previous, current, next characters
      p = i-1 >= 0 ? tokens[i-1] : ''
      c = tokens[i]
      n = i+1 < tokens.size ? tokens[i+1] : ''

      if c == '\'' && p != '\\'  # Quote
        in_single_quote = !in_single_quote
      end
      if c == '"' && p != '\\'  # Quote
        in_double_quote = !in_double_quote
      end
      if c == '/' && n == '/'  # Comment
        in_comment = true
      end
      if c == '/' && n == '*'  # Begin multi-comment
        in_multi_comment = true
      end

      if !(in_single_quote || in_double_quote || in_comment || in_multi_comment || c == '')
        # Replace if not in quote or comment
        if c == ',' # One space after
          c += ' ' if n != ' '
        elsif ['++', '--'].index(c+n)  # Double character operators
          c = c+n
          n = ''
        elsif ['==', '!=', '<=', '>=', '+=', '-=', '*=', '/=', '&=', '|=', '&&', '||'].index(c+n)  # Double character operators
          c = c+n
          n = ''
          c = ' ' + c if p != ' '
          c = c + ' ' if tokens[i+2] != ' '
        elsif '*/=%'.index(c)  # Single character operators
          # Don't do <, > because of generics
          # Don't do ,  + because of unaries
          c = ' ' + c if p != ' '
          c = c + ' ' if n != ' '
        end

        # Write back
        tokens[i] = c
        tokens[i+1] = n if i+1 < tokens.size
      end

      if p == '*' && c == '/'  # End multi-comment
        in_multi_comment = false
      end
    }
    new_line = tokens.join('')
    #p new_line

    new_line.gsub!(/\. \* ;/, '.*;')
    new_line.gsub!(/\s+$/, '')

    if line != new_line
      puts "======= #{path} #{line_num}"
      puts "OLD: [#{line}]"
      puts "NEW: [#{new_line}]"
    end

    new_line
  }

  # Write it out
  if mutate
    out = open(path, 'w')
    out.puts lines
    out.close
  end
}
