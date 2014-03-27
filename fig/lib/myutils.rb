# Basic utilities version 1.0

module Enumerable
  Log2 = Math.log(2)
  def sum; x = 0; each { |y| x += y }; x end
  def prod; x = 1; each { |y| x *= y }; x end
  def mean; sum*1.0 / size end
  def var; m = mean; map{|x| (x-m)**2}.mean end
  def stddev; Math.sqrt(var) end
  def entropy
    x = 0; s = sum.to_f
    each { |y| x += -y/s*Math.log(y/s)/Log2 if y > 0 }; x
  end
  def to_i; map{|x| x.to_i} end
  def to_f; map{|x| x.to_f} end
end

def round(x, n=0)
  return (x+0.5).to_i if n == 0
  b = 1; n.times { b *= 10 }
  (x*b+0.5).to_i.to_f / b
end

def fact(n)
  prod = 1
  (1..n).each { |x| prod *= x }
  prod
end
def choose(n, k)
  k = [k, n-k].min
  prod = 1
  (n-k+1..n).each { |x| prod *= x }
  (1..k).each { |x| prod /= x }
  prod
end

def new_matrix(nr, nc, x=nil)
  a = []
  (0...nr).each { |r| a[r] = [] }
  (0...nr).each { |r| (0...nc).each { |c| a[r][c] = x } }
  a
end

def linspace(a, b, n)
  (0...n).each { |i|
    yield 1.0 * i / (n-1) * (b - a) + a
  }
end

class Array
  def transpose
    a = []
    each_with_index {|ar,r| ar.each_with_index { |x,c| (a[c] ||= [])[r] = x } }
    a
  end
  def matrix_each
    each_with_index { |ar,r| ar.each_with_index { |x,c| yield x, r, c } }
  end
  def map_index
    a = []
    each_index { |i| a << yield(i) }
    a
  end
  def map_with_index
    a = []
    each_with_index { |x,i| a << yield(x, i) }
    a
  end
  def tail; self[1...size] end
  def incr(i, x=1); if at(i) then self[i] += x; else self[i] = x end end
  def col(i); a = []; each {|r| a << r[i]}; a end # Column i of a matrix

  def argmax
    m = nil
    each_index { |i| m = i if m == nil || self[i] > self[m] }
    m
  end
  def argmin
    m = nil
    each_index { |i| m = i if m == nil || self[i] < self[m] }
    m
  end
  def sample
    target = rand * sum
    accum = 0
    i = 0
    while i < size
      accum += self[i]
      return i if accum >= target
      i += 1
    end
    raise "Impossible: #{inspect}"
  end

  # Mutations
  def normalize; s = sum*1.0; each_index{|i| self[i] /= s}; self end
  def shuffle;
    each_index { |i|
      j = rand(size-i)+i
      t = self[i]; self[i] = self[j]; self[j] = t
    }
    self
  end
end

class Hash
  def dump; each_pair { |k,v| puts "#{k}\t#{v.inspect}" } end
  def diff(h) # Return self - h
    d = {}; each_key { |k| d[k] = true unless h.has_key? k }; d
  end
  def union(h) # Return self \cup h
    clone.union!(h)
  end
  def union!(h) # self := self \cup h
    h.each_key { |k| self[k] = true }; self
  end
  def intersection(h)
    clone.intersection!(h)
  end
  def intersection!(h)
    each_key { |k| self.delete(k) unless h.has_key?(k) }
  end
  def Hash.from_a(a) # Convert from array
    h = {}
    a.each { |x| h[x] = true }
    h
  end
  def incr(k, dv=1); self[k] = (self[k] || 0) + dv end
  def push(k, x); (self[k] = self[k] || []).push(x) end
  def urlencode
    collect { |k,v| "#{k.to_s.urlencode}=#{v.to_s.urlencode}" }.join('&')
  end
end

class Dir
  def child_entries(dir)
    entries.reject(dir) { |x| x == "." || x == ".." }
  end
end

class String
  def urlencode
    gsub(/[^a-zA-Z0-9\-_\.!~*'()]/n) {|x| sprintf('%%%02x', x[0])}
  end
  def trim
    sub(/^\s+/, "").sub(/\s+$/, "")
  end
end

class IO
  def IO.writelines(file, lines)
    out = Kernel.open(file, "w")
    lines.each { |line| out.puts line }
    out.close
  end
end

############################################################

class Indexer
  def initialize
    @i2o = []
    @o2i = {}
  end
  def indexOf(o)
    @o2i[o] || -1
  end
  def getIndex(o)
    i = @o2i[o]
    if i == nil then
      i = size
      @o2i[o] = i
      @i2o[i] = o
    end
    i
  end
  def getObject(i)
    @i2o[i]
  end

  def size; @i2o.size end
end

############################################################

# Simple way to process command-line arguments
# Return [value1, ... valueK]; modifies args
# If remove, we remove the used arguments from args.
# Each element of names is either a string name
# or a tuple [name, type, default value, required, description].
def extractArgs(options)
  d = lambda { |x,y| x != nil ? x : y }
  args = options[:args] || ARGV
  remove = d.call(options[:remove], true)
  spec = options[:spec] || []
  recognizeAllOpts = d.call(options[:recognizeAllOpts], true)

  arr = lambda { |x| x.is_a?(Array) ? x : [x] }
  spec = spec.compact.map { |x| arr.call(x) }
  names = spec.map { |x| x[0] }
  types = spec.map { |x| x[1] || String }
  values = spec.map { |x| x[2] != nil ? arr.call(x[2]) : nil } # Default values, to be replaced
  requireds = spec.map { |x| x[3] }
  descriptions = spec.map { |x| x[4] }

  # Print help?
  args.each { |arg|
    if arg == '-help'
      puts 'Usage:'
      spec.each { |name,type,value,required,description|
        puts "  -#{name}: #{type} [#{value}]#{required ? ' (required)' : ''}#{description ? ': '+description : ''}"
      }
    end
  }
  
  newArgs = [] # Store the arguments that we don't remove
  i = nil
  verbatim = false
  persistentVerbatim = false
  args.each { |arg|
    if arg == '--' and not persistentVerbatim then
      verbatim = true
    elsif arg == '---' then
      persistentVerbatim = !persistentVerbatim
    elsif (not verbatim) && (not persistentVerbatim) && arg =~ /^-(.+)$/ then
      x = $1
      #i = names.index($1)
      # If $1 is the prefix of exactly one name in names, then use that
      matchi = [names.index(x)].compact # Try exact match first
      matchi = names.map_with_index { |name,j| name =~ /^#{x}/ ? j : nil }.compact if matchi.size == 0
      if recognizeAllOpts then
        if matchi.size == 0
          puts "No match for -#{x}"
          exit 1
        elsif matchi.size > 1
          puts "-#{x} is ambiguous; possible matches: "+matchi.map{|i| "-"+names[i]}.join(' ')
          exit 1
        end
      end
      i = (matchi.size == 1 ? matchi[0] : nil)

      values[i] = [] if i
      verbatim = false
    else
      values[i] << arg if i
      verbatim = false
    end
    newArgs << arg unless remove && i
  }
  args.clear
  newArgs.each { |arg| args << arg }

  (0...names.size).each { |i|
    if requireds[i] && (not values[i]) then
      puts "Missing required argument: -#{names[i]}"
      exit 1
    end
  }

  # Interpret values according to the types
  values.each_index { |i|
    next if values[i] == nil
    t = types[i]
       if t == String    then values[i] = values[i].join(' ')
    elsif t == Fixnum    then values[i] = Integer(values[i][0])
    elsif t == Float     then values[i] = Float(values[i][0])
    elsif t == TrueClass then values[i] = (values[i].size == 0 || values[i][0].to_s == 'true')
    elsif t.is_a?(Array) then
      t = t[0]
         if t == String    then values[i] = values[i]
      elsif t == Fixnum    then values[i] = values[i].map { |x| Integer(x) }
      elsif t == Float     then values[i] = values[i].map { |x| Float(x) }
      elsif t == TrueClass then values[i] = values[i].map { |x| x == 'true' }
      else "Unknown type: '#{types[i][0]}'"
      end
    else raise "Unknown type: '#{types[i]}'"
    end
  }

  values
end

############################################################

class StructVar
  attr_accessor :name, :isList, :initDefaultVal, :setDefaultVal, :getDefaultVal
  def initialize(options)
    @name = options[:name].to_s
    @isList = options[:isList]
    @initDefaultVal = options[:initDefaultVal]
    @setDefaultVal = options[:setDefaultVal]
    @getDefaultVal = options[:getDefaultVal]
  end
end
def structVar(options); StructVar.new(options) end
def structVarVal(name, initDefaultVal)
  StructVar.new(:name => name, :initDefaultVal => initDefaultVal)
end

# Defines a class with a given name and arguments (for the constructor)
# name is the name of the class
# vars is a list of (var name or StructVar objects)
def defineStruct(options)
  name = options[:name] or raise 'Need name'
  vars = options[:vars] || []
  to_sVar = options[:to_sVar] || false
  initHash = options[:initHash] || false # Whether the initialize() function takes a hash
  easySetAccessorFunc = options[:easySetAccessorFunc] || false # Define functions .x and .getX
  inheritFuncs = options[:inheritFuncs] || [] # A list of [var, prefix, [subvar]]

  standardizeVars = lambda { |_vars|
    _vars.compact.map { |var|
      var.is_a?(StructVar) ? var : StructVar.new(:name => var)
    }
  }

  vars = standardizeVars.call(vars)

  lines = []
  lines << "class #{name}"

  default = lambda { |x,y| x.to_s + (y ? " || #{y}" : "") }
  cap = lambda { |s| s != '' ? s[0..0].upcase + s[1..-1] : '' }

  # Getter and setters
  if easySetAccessorFunc
    vars.each { |var|
      name = var.name.to_s
      arg = (var.isList ? "*" : "") + name + (var.setDefaultVal != nil ? "=#{var.setDefaultVal}" : "")
      lines << "  def #{name}(#{arg}); @#{name} = #{name}; self end" # Setter
      lines << "  def get#{cap.call(name)}; #{default.call('@'+name, var.getDefaultVal)} end" # Getter
    }
    inheritFuncs.each { |var,prefix,subvars|
      standardizeVars.call(subvars).each { |subvar|
        name = subvar.name.to_s
        arg = (subvar.isList ? "*" : "") + name + (subvar.setDefaultVal != nil ? "=#{subvar.setDefaultVal}" : "")
        lines << "  def #{prefix}#{name}(#{arg}); @#{var}.#{name}(#{name}); self end" # Setter
        lines << "  def get#{cap.call(prefix)}#{cap.call(name)}; @#{var}.get#{cap.call(name)} end" # Getter
      }
    }
  else
    lines << "  attr_accessor " + vars.map{|var| ":#{var.name}"}.join(', ')
  end

  # Initialize (constructor)
  if initHash
    args = "options"
  else
    args = vars.map { |var|
      var.name.to_s + (var.initDefaultVal != nil ? "=#{var.initDefaultVal}" : '')
    }.join(', ')
  end
  lines << "  def initialize(#{args})"
  vars.each { |var|
    defClause = var.initDefaultVal ? " || #{var.initDefaultVal}" : ""
    name = initHash ? "options[:#{var.name}]" : var.name
    lines << "    @#{var.name} = #{name}"
  }
  lines << "  end"

  if to_sVar
    lines << "  def to_s; @#{to_sVar} end"
  end

  lines << "end"
  #puts lines
  eval lines.join("\n")
end

def debug(*l); puts "DEBUG " + l.inspect end

def default(x, y); x != nil ? x : y end # Serves || but false is not treated as nil
def applyIf(x, &block); x != nil ? block.call(x) : nil end

############################################################

# Use this to run system commands so that processes don't get orphaned.
class ProcessManager
  @@child_pids = {}
  at_exit {
    if @@child_pids.size > 0
      puts "ProcessManager: killing child processes to avoid orphaning: #{@@child_pids.keys.join(' ')}"
      @@child_pids.keys.each { |pid| Process::kill("TERM", pid) }
    end
  }

  # Return whether the process succeeded or not
  def self.system(*cmds)
    pid = fork { exec(*cmds) }
    @@child_pids[pid] = true
    begin
      Process::wait(pid)
      @@child_pids.delete(pid)
    rescue Exception => e
      puts "ProcessManager: interrupted"
    end
    $? == 0
  end
  def self.systemOrFail(*cmds)
    self.system(*cmds) or raise "Command failed: #{cmds.size == 1 ? cmds[0] : cmds.inspect}"
  end
end

def pipe(cmd, lines)
  f = IO.popen(cmd, "r+")
  lines.each { |line| f.puts line }
  f.close_write
  newLines = []
  while line = f.gets
    newLines << line
  end
  f.close
  newLines
end

def downloadURL(url)
  path = cachedPath(url)
  systemOrRaise "wget --user-agent='Mozilla Firefox' -q -O '#{path}' '#{url}'" if not File.exists?(path) # Cache it
  IO.read(path)
end
def downloadURLLines(url); downloadURL(url).split(/\n/) end

# Important: lines must end with new lines
# Example: (a b c "a\n\t \"b" (a d))
def parseLispTree(lines)
  result = nil
  foreachLispTree(lines) { |tree|
    raise "Got more than one tree" if result
    result = tree
  }
  result
end
def parseLispTrees(lines)
  result = []
  foreachLispTree(lines) { |tree| result << tree }
  result
end
def foreachLispTree(lines)
  lines = [lines] if lines.is_a?(String)
  line_num = 0
  line = nil # Current line
  i = -1 # Current position in line
  n = 0 # Length of line
  c = nil # Current character

  # Invariant after first advance: we're sitting on a character
  error = lambda { |msg| raise "#{msg} on line #{line_num}: #{line}" }
  advance = lambda {
    i += 1
    # If exhausted line, then go to next
    while i == n
      line = lines[line_num]
      if not line
        i = n = 0
        c = nil
        break
      end
      line_num += 1
      n = line.size
      i = 0
    end
    c = line[i] if line
  }
  skipSpace = lambda {
    while c
      if c == ?# # Comment: Ignore to the end of the line
        while c && c != "\n"[0]
          advance.call
        end
      elsif " \t\n".index(c) # Whitespace
        advance.call
      else # Regular character
        break
      end
    end
  }
  recurse = lambda {
    skipSpace.call
    if not c # Nothing
      nil
    elsif c == ?( # List
      advance.call
      tree = []
      while true
        skipSpace.call
        if not c
          error.call "Missing ')'"
        elsif c == ?) # End
          advance.call
          break
        end
        tree << recurse.call
      end
      tree
    else # Primitive
      error.call "Extra ')'" if c == ?)
      escaped = false
      in_quote = false
      value = ""
      while c
        if escaped
          if c == ?n # Newline
            value << "\n"
          elsif c == ?t # Tab
            value << "\t"
          else
            value << c
          end
          escaped = false
        elsif c == ?\\
          escaped = true
        elsif c == ?"
          in_quote = !in_quote
        else
          break if (not in_quote) && (" \t\n".index(c) || c == ?))
          value << c
        end
        advance.call
      end
      error.call "Missing escaped character" if escaped
      error.call "Missing end quote" if in_quote
      value
    end
  }

  advance.call # Init
  while result = recurse.call
    yield result
  end
end
def renderLispTree(tree)
  s = ""
  recurse = lambda { |tree|
    if tree.is_a?(Array)
      s << ?(
      tree.each_with_index { |x,i|
        s << ' ' if i > 0
        recurse.call(x)
      }
      s << ?)
    else
      x = tree.to_s.gsub(/\\/, "\\\\\\\\").gsub(/"/, "\\\"")
      x = '"'+x+'"' if x =~ /[\s\(\)#]/
      s << x
    end
  }
  recurse.call(tree)
  s
end

# For JRuby: recursively convert Java arrays to Ruby arrays
if defined? JRUBY_VERSION
  require 'java'
  $javaArrayClass = [].to_java.class
  class Object
    def javaArray_?; self.class == $javaArrayClass end
    def recursive_to_java
      if is_a?(Array)
        map { |y| y.recursive_to_java }.to_java
      else
        self
      end
    end
    def recursive_to_ruby
      if javaArray_?
        (0...size).map { |i| self[i].recursive_to_ruby }
      else
        self
      end
    end
  end
end
