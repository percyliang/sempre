#!/usr/bin/env ruby

require 'open-uri'

if ARGV.size != 2 then
  puts "Usage: <SPARQL endpoint (e.g., http://jonsson:3093/sparql)> <schema.ttl file>"
  exit 1
end
$endpoint, $outPath = ARGV

# Return set of raw lines from the query (probably don't want to use this function directly)
def makeQuery(query)
  queryUrl = URI::encode("PREFIX fb: <http://rdf.freebase.com/ns/> #{query}")
  #puts query
  `curl -s '#{$endpoint}?query=#{queryUrl}'`.split(/\n/)
end

def getCount(query)
  n = nil
  lines = makeQuery(query)
  lines.each { |line|
    # <binding name="callret-0"><literal datatype="http://www.w3.org/2001/XMLSchema#integer">6828</literal></binding>
    n = Integer($1) if line =~ /<binding.*>(\d+)<\/literal>/
  }
  puts "Bad: #{query} lead to #{lines}" if not n
  "\"#{n || 0}\"^^xsd:int"
end
$zero = '"0"^^xsd:int'

# Returns (via yield) entries
def getEntries(query)
  batchSize = 20000
  offset = 0
  query =~ /select\s*(.*)\s*\{/ or raise "No variables in query: #{query}"
  vars = $1.split.map { |v|
    v =~ /^\?(.+)$/ or raise "Bad variable: #{v}"
    $1
  }
  while true
    # Output:
    #   <result>
    #     <binding name="x"><uri>http://rdf.freebase.com/ns/m.07p4yzl</uri></binding>
    #     <binding name="y"><uri>http://rdf.freebase.com/ns/type.object.name</uri></binding>
    #     <binding name="z"><literal xml:lang="en">Project</literal></binding>
    #   </result>
    n = 0
    map = {}
    makeQuery(query + " LIMIT #{batchSize} OFFSET #{offset}").each { |line|
      #puts line
      if line =~ /<result>/
        map = {}
      elsif line =~/<binding name="(\w+)">(.+)<\/binding>/
        map[$1] = convert($2)
      elsif line =~ /<\/result>/
        if not vars.all?{|v| map[v]}
          puts "ERROR: invalid map: " + map.inspect
        else
          yield vars.map{|v| map[v]}
        end
        map = nil
        n += 1
      end
    }
    puts "  offset #{offset}: #{n} entries"
    break if n == 0
    offset += n
  end
end

def convert(s)
  if s =~ /rdf.freebase.com\/ns\/([^<]+)</
    "fb:" + $1
  elsif s =~ /"en">([^<]+)</
    "\"" + $1 + "\"@en"
  elsif s =~ /integer">(\d+)<\/literal>/
    # Note: Virtuoso represents booleans as integers.
    # We assume the schema only has booleans (0, 1).
    # <literal datatype="http://www.w3.org/2001/XMLSchema#integer">1</literal>
    if $1 == '1'
      '"true"^^xsd:boolean'
    elsif $1 == '0'
      '"false"^^xsd:boolean'
    else
      raise "Unsupported: " + s
    end
  else
    puts "ERROR: " + s
    nil
  end
end

def putsTriple(out, x, p, y)
  out.puts [x, p, y].join("\t") + '.'
  out.flush
end

def extractAll
  out = open($outPath, 'w')

  # This structure describes all the simple unary/binary predicates we will care about.
  # Each entry
  #  - Property p (for unaries)
  #  - Type t
  # Unaries are defined by (*, p, y) for each y \in t
  # Binaries are defined by (*, p, *) for each p \in t
  predicateInfos = [
    [nil, 'fb:type.property'], # Binary
    ['fb:type.object.type', 'fb:type.type'], # Unary
    ['fb:people.person.profession', 'fb:people.profession'], # Unary
    ['fb:people.person.nationality', 'fb:location.country'], # Unary
    ['fb:people.person.ethnicity', 'fb:people.ethnicity'], # Unary
  nil].compact

  # Print number of instances for each predicate
  hasSupport = {}
  numTotalPredicates = 0
  predicateInfos.each { |p,t|
    puts "Extracting num_instances for each member of #{t}"
    getEntries("select ?x { ?x fb:type.object.type #{t} }") { |x|
      x = x[0]
      if p
        n = getCount("select count(?x) { ?x #{p} #{x} }") # Unary
      else
        n = getCount("select count(*) { ?x #{x} ?y }") # Binary
      end
      numTotalPredicates += 1
      next if n == $zero # Skip predicates with no support

      hasSupport[x] = true
      if p
        putsTriple(out, x, p.sub(/^fb:/, 'fb:user.custom.') + '.num_instances', n)
      else
        putsTriple(out, x, 'fb:user.custom.type.property.num_instances', n)
      end
      puts "  #{hasSupport.size} predicates" if hasSupport.size % 1000 == 0
      #break if hasSupport.size > 100
    }
  }
  puts "#{hasSupport.size}/#{numTotalPredicates} predicates with support"

  # Print name/alias
  predicateInfos.each { |p,t|
    ['fb:type.object.name', 'fb:common.topic.alias'].each { |q|
      puts "Extracting #{q} for each member of #{t}"
      getEntries("select ?x ?y { ?x fb:type.object.type #{t}. ?x #{q} ?y }") { |x,y|
        next unless hasSupport[x]
        putsTriple(out, x, q, y)
      }
    }
  }

  # Output information for types and properties.
  [
    'fb:type.property.schema',
    'fb:type.property.expected_type',
    'fb:type.property.unit',
    'fb:type.property.reverse_property',
    'fb:freebase.type_hints.mediator',
    'fb:freebase.type_hints.included_types',
  nil].compact.each { |p|
    unique = (p == 'fb:type.property.schema') || (p == 'fb:type.property.expected_type')
    items = {}
    puts "Extracting #{p}"
    getEntries("select ?x ?y { ?x #{p} ?y }") { |x,y|
      next unless hasSupport[x]
      if unique and items[x]
        puts "ERROR: #{x} #{p} #{items[x]} already exists, trying to add #{y}, skipping..."
        next
      end
      items[x] = y
      putsTriple(out, x, p, y)
    }
  }

  out.close
end

extractAll
