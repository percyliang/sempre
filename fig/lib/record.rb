#!/usr/bin/ruby

# Read/write/search through files written by fig.record.Record.
# Doesn't handle .file special key word.
class Record
  def initialize(root)
    @root = root
  end

  def Record.read(path="/dev/stdin")
    root = ['ROOT_KEY', 'ROOT_VALUE']
    stack = [root]
    structKeys = nil
    IO.foreach(path) { |line|
      line.chomp!
      line =~ /^(\t*)([^\t]+)\t?(.*)$/
      indent = $1.size
      while indent < stack.size-1 do # Unindent
        stack.pop
        structKeys = nil
      end
      if indent == stack.size # Indent
        stack << stack[-1][-1]
        structKeys = nil
      end
      raise "Indent messed up: #{indent} != #{stack.size-1}" if indent != stack.size-1

      if $2 == ".struct" then
        structKeys = $3.split /\t/
      elsif $2 == ".array" then
        key, *values = $3.split /\t/
        values.each { |value| stack[-1] << [key, value] }
      else
        if structKeys then
          # Construct a node
          values = [$2] + $3.split(/\t/)
          node = [structKeys[0], values[0]]
          (1...structKeys.size).each { |i|
            node << [structKeys[i], values[i]]
          }
          stack[-1] << node
        else
          stack[-1] << [$2, $3] # Add node
        end
      end
    }
    Record.new(root)
  end

  def write(path=nil)
    out = path ? open(path, "w") : $stdout
    helper = lambda { |node,indent|
      out.puts(("\t"*(indent-1)) + node[0] + (node[1] ? "\t"+node[1] : "")) if indent > 0
      node[2..-1].each { |childNode| helper.call(childNode, indent+1) }
    }
    helper.call(@root, 0)
    out.close if out != $stdout
  end

  # Return a list of matching nodes
  def find(*queryKeyValueList)
    results = []
    queryKeyValueList = queryKeyValueList.map { |kv|
      kv = [kv] if not kv.is_a?(Array)
      kv.map { |a| a.to_s }
    }
    # Of node's children, return the matches
    helper = lambda { |node,i|
      results << node if i == queryKeyValueList.size
      # For each children node of node
      queryKey, queryValue = queryKeyValueList[i]
      node[2..-1].each { |childNode|
        # Skip children that don't match
        next if not (queryKey == nil || queryKey == childNode[0])
        next if not (queryValue == nil || queryValue == childNode[1])
        helper.call(childNode, i+1)
      }
    }
    helper.call(@root, 0)
    results
  end
end
