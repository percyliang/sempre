#!/usr/bin/ruby

"""
The execrunner library provides an easy way to generate a set of commands which
share a lot of arguments.  For example, suppose you wanted to run the following

  my_program -C 3 -numIters 5 -greedy false
  my_program -C 3 -numIters 5 -greedy true
  my_program -C 3 -numIters 10 -greedy false
  my_program -C 3 -numIters 10 -greedy true

You can write the following snippet of Ruby:

  require 'execrunner'
  run!(
    'my_program',
    o('C', 3),
    selo(nil, 'numIters', 5, 10),
    selo(nil, 'greedy', false, true),
  nil)

Call this script run.

To print out all the commands that would be executed:
  ./run -n
To execute all of them sequentially:
  ./run [<additional arguments>]

The general usage to try all settings is:
  selo(nil, argument, value_1, ..., value_n)

To choose the i-th particular setting (set i to one of 0, 1, 2, etc.):
  selo(i, argument, value_1, ..., value_n)
When you're doing experimentation, it's often convenient to switch back
and forth between several commonly used options, and execrunner allows
you to do this by just changing i.

You can specify i from the command-line too.  In code, you can put :mode in
place of i:
  sel(:mode,
    o('create'),
    o('destroy'),
  nil),
To select destroy on the command-line, do:
  ./run @mode=1

There are more advanced features, but this is just the basics.
"""

# @<tag>=<value>: run commands only with the environment set

# Special tags (set using let, for example, let(:tag, 'foobar'))
# easy: if command fails, press on
# appendArgs: arguments to put at very end

require File.dirname(__FILE__)+'/myutils'

# Override if necessary
$optPrefix = '-'
$optAppendPrefix = '+'
# For key/value pair, nil means key and value are separate args; otherwise, output one arg
# with key and value separated by the connective
$optConnective = nil

class Prog
  attr_accessor :x
  def initialize(x); @x = x; end
end
class Let
  # If override is not set, then don't assign if |var| already has a value.
  attr_accessor :var, :value, :append, :override
  def initialize(var, value, append, override); @var = var; @value = value; @append = append; @override = override end
end
class Prod
  attr_accessor :choices
  def initialize(choices); @choices = choices end
end
class Stop; end

class Env
  attr_accessor :list
  def initialize(isTerminal, list)
    @isTerminal = isTerminal
    @list = standarizeList(list.flatten)
  end

  def getRuns(runner, list, args, bindings)
    if list.size == 0
      runner.runArgs(args, bindings) if @isTerminal
      return
    end

    x, *rest = list
    case x
    when Array then
      getRuns(runner, x+rest, args, bindings)
    when Let then # Temporarily modify bindings
      if x.override || (not bindings.has_key?(x.var))
        oldvalueExists = bindings.has_key?(x.var)
        oldvalue = bindings[x.var]
        #puts "Add #{oldvalue.inspect} #{x.value.inspect}"
        bindings[x.var] = x.append && oldvalue ? oldvalue+x.value : x.value
        getRuns(runner, rest, args, bindings)
        if oldvalueExists
          bindings[x.var] = oldvalue
        else
          bindings.delete(x.var)
        end
      else
        getRuns(runner, rest, args, bindings)
      end
    when Symbol # Substitute bindings with something else
      raise "Variable not bound: '#{x}'" unless bindings[x]
      getRuns(runner, [bindings[x]]+rest, args, bindings)
    when Prod then # Branch on several choices (each choice is a list)
      x.choices.each { |choice|
        getRuns(runner, choice+rest, args, bindings)
      }
    when Env then # Branch
      x.getRuns(runner, x.list, args, bindings)
      getRuns(runner, rest, args, bindings)
    when Prog then # Set the program
      getRuns(runner, rest, args+[x], bindings) # Just add to arguments
    when Proc then # Lazy value
      # Two ways to deal with lazy values: (example: run(lambda{f}, sel(nil,a,b)))
      #  1) evaluate f once for a and once for b (in this case, f can only contain primitive data)
      #  2) evaluate f once before the sel (f can contain complex things like another product)
      #     getRuns(runner, rest, args+[x], bindings) # Just add to arguments - evaluate later
      # We chose 2.
      getRuns(runner, [x.call(bindings)]+rest, args, bindings)
    else # String
      getRuns(runner, rest, args+[x.to_s], bindings) # Just add to arguments
    end
  end

  def to_s; "env(#{@list.size})" end
end

class ExecRunner
  attr :extraArgs

  def initialize(prog, extraArgs)
    @prog = prog
    setExtraArgs(extraArgs)
  end
  def setExtraArgs(extraArgs)
    @pretend = extraArgs.member?("-n") # Don't execute, just print out command-line
    @specifiedTags = extraArgs.map { |x| x =~ /^@(\w+)$/ ? $1 : nil }.compact

    @initEnv = {}
    extraArgs.each { |x|
      next unless x =~ /^@(\w+)=(.+)$/
      k, v = $1.to_sym, $2
      if v == 'nil'
        v = nil
      elsif v =~ /^\d+$/
        v = Integer(v)
      end
      @initEnv[k] = v
    }

    # Remove the options and tags that we just extracted
    @extraArgs = extraArgs.clone.delete_if { |x|
      x =~ /^-n$/ || x =~ /^@/
    }
  end

  # Specify tag to run a command
  def requireTags(v=true); @requireTags = v; nil end

  def memberOfAny(a, b)
    return false unless b
    b.each { |x|
      return true if a.member?(x)
    }
    false
  end

  # Run the command with the arguments
  def runArgs(args, bindings)
    # Skip if user specified some tags but none of the current tags
    # match any of the specified tags
    if @requireTags || @specifiedTags.size > 0
      return if (not memberOfAny(@specifiedTags, bindings[:tag]))
    end

    args = args.clone
    args += @extraArgs unless bindings[:ignoreExtraArgs]
    args = bindings[:prependArgs] + args if bindings[:prependArgs]
    args = args + bindings[:appendArgs] if bindings[:appendArgs]

    args = args.map { |x| x.is_a?(Proc) ? x.call : x } # Evaluate lazy items

    if @pretend
      puts args.join(' ') # Not quoted
    else
      success = ProcessManager::system(*args)
      if (not success) && (not bindings[:easy])
        puts "Command failed: #{args.join(' ')}"
        exit 1
      end
    end
  end

  def execute(e); e.getRuns(self, e.list, [], @initEnv) end 
end

############################################################

$globalExecRunner = ExecRunner.new(nil, ARGV)

def env(*list); Env.new(false, list) end
def run(*list); Env.new(true, list) end
def env!(*x); $globalExecRunner.execute(env(*x)) end
def run!(*x); $globalExecRunner.execute(run(*x)) end

def prog(*x); $globalExecRunner.prog(*x) end
def tag(v); let(:tag, [v], true) end
def easy(v=true); let(:easy, v) end
def ignoreExtraArgs(v=true); let(:ignoreExtraArgs, v) end
def requireTags(v=true); $globalExecRunner.requireTags(v) end
def appendArgs(*v); let(:appendArgs, v) end
def prependArgs(*v); let(:prependArgs, v) end
def note(*x); a('note', *x) end
def misc(*x); a('miscOptions', *x) end
def tagstr; lambda { |e| e[:tag].join(',') } end
def l(*list); standarizeList(list) end

# Options
def o(key, *values); optAppendOrNot(false, key, *values) end
def a(key, *values); optAppendOrNot(true, key, *values) end
def optAppendOrNot(append, key, *values)
  lambda { |e|
    values = standarizeList(values.flatten).map { |value| value && envEval(e, value).to_s }
    values = ['---']+values+['---'] if values.map { |x| x =~ /^-/ ? x : nil }.compact.size > 0 # Quote values: -x -0.5   =>   -x --- -0.5 ---
    prefixKey = "#{append ? $optAppendPrefix : $optPrefix}#{key}"
    if $optConnective
      prefixKey + $optConnective + values.join(' ')
    else
      [prefixKey] + values
    end
  }
end

# Selection functions
def sel(i, *list)
  #list = standarizeList(list)
  #map = toMap(list)
  #i == nil ? prod(*map.values) : lambda {|e| map[envEval(e,i)]}
  general_sel(i, nil, list, false, lambda{|*z| l(*z)})
end
def selo(i, name, *list); general_sel(i, name, list, false, lambda{|*z| o(*z)}) end
def selotag(i, name, *list); general_sel(i, name, list, true, lambda{|*z| o(*z)}) end
def sellet(i, name, *list); general_sel(i, name, list, false, lambda{|*z| let(*z)}) end
def sellettag(i, name, *list); general_sel(i, name, list, true, lambda{|*z| let(*z)}) end
def general_sel(i, name, list, useTags, baseFunc)
  # baseFunc is one of {opt,let}
  list = standarizeList(list)
  map = toMap(list)
  if i == nil # Product over all possible values
    values = isHash(list) ? map.values : list
    prod(*values.map {|v| l(baseFunc.call(name, v), useTags ? tag("#{name}=#{v}") : nil)})
  else
    lambda { |e|
      key = envEval(e,i)
      if key == nil
        general_sel(key, name, list, useTags, baseFunc)
      else
        unless map.has_key?(key)
          puts "Value #{key.inspect} (for key #{i.inspect}) is invalid; possible values are #{map.keys.inspect}"
          exit 1
        end
        v = map[key]
        #p [key, v, map, e]
        l(baseFunc.call(name, v), useTags ? tag("#{name}=#{v}") : nil)
      end
    }
  end
end
def isHash(list); list.size == 1 && list[0].is_a?(Hash) end
def toMap(list)
  if isHash(list) then list[0]
  else h = {}; list.compact.each_with_index { |x,i| h[i] = x }; h
  end
end

def prog(x); Prog.new(x) end
def let(var, value, append=false); Let.new(var, value, append, true) end
def letDefault(var, value, append=false); Let.new(var, value, append, false) end
def prod(*choices)
  Prod.new(standarizeList(choices).map {|choice| choice.class == Array ? choice : [choice]})
end
def stop; Stop.new end

# Example usages: view(3)
def view(map); o('addToView', map) end
def tagview(x); l(view(x), tag(x)) end

# Helper function: evaluate v in environment e
def envEval(e,v)
  case v
  when Proc then envEval(e, v.call(e))
  when Symbol then
    unless e.has_key?(v)
      puts "Key #{v.inspect} is not in environment #{e.inspect}"
      exit 1
    end
    envEval(e, e[v])
  else v
  end
end

# Helper function: compact the list of arguments
# Also, remove anything after stop object
def standarizeList(list)
  hasStopped = false
  list.map { |x|
    hasStopped = true if x.is_a?(Stop) 
    hasStopped ? nil : x
  }.compact
end

# Ensure that a certain tag (@mode=...) exists.
def required(tag, description=nil)
  description = " [#{description}]" if description 
  lambda { |e|
    if not e.has_key?(tag)
      puts "Missing required tag #{tag.inspect}#{description}"
      exit 1
    end
    l()
  }
end
