#!/usr/bin/env ruby

require 'open-uri'
require 'json'
require 'nokogiri'
require 'socket'

<<EOF
A shell for exploring the Freebase graph.
Uses the Freebase Search API to lookup entities.
Uses the cache server to map from mid to canonical ids.
Uses SPARQL queries to lookup graph edges.

At each point in time we have a state which yields a list of new states.
Here are the possible types of states

- State: entity (example: fb:en.barack_obama)
  List: list of (entity, property) states.

- State: (entity, property) (example: fb:en.barack_obama fb:people.person.children)
  List: list of entity values.

- State: query string (example: obama)
  List: list of entity matches from Freebase Search.

- State: (entity, target) (example: fb:en.barack_obama [fb:en.michelle_obama])
  List: list of (entity, property) that lead to the target.

- State: (entity, property, target) (example: fb:en.barack_obama fb:en.profession [fb:en.bill_clinton])
  List: list of entity values that match from both entity and target from property.
  TODO

Commands:

  obama                     search 
  --michelle obama          find paths to target
  3                         push an element
  -3                        show an element but don't push it
  ..                        pop the stack
  !                         compute listing for all items in list
EOF

$server = 'freebase.cloudapp.net'

$mid2id = TCPSocket.new($server, 4000)
def call(command)
  $mid2id.puts command
  result = $mid2id.gets.strip
  return nil if result == '__NULL__'
  return result
end
call("open\tfreebase-rdf-2013-06-09-00-00.canonical-id-map.gz")

# Explore the Freebase graph
def fbsearch(query)
  return [{'id' => query}] if query =~ /^fb:/  # Specify ID verbatim
  raw = open('https://www.googleapis.com/freebase/v1/search?query=' + URI::encode(query)).read
  json = JSON::parse(raw)
  results = json['result']
  results.map { |r|
    mid = r['mid'].gsub!(/^\//, 'fb:').sub!(/\//, '.')  # /m/02mjmr => fb:m.02mjmr
    id = r['id'] = call("get\t" + mid)  # Canonicalize the ids to be consistent with our Freebase index
    if not id
      puts "Skipping entry with no id: #{r.inspect}"
      nil
    else
      r
    end
  }.compact
end

def sparql(query)
  query = 'PREFIX fb: <http://rdf.freebase.com/ns/> ' + query + ' LIMIT 1000'
  encodedQuery = URI::encode(query).gsub(/\+/, '%2b')
  queryUrl = "http://#{$server}:3093/sparql?query=#{encodedQuery}"
  raw = open(queryUrl).read
  raw.sub!(/<sparql.*>/, '<sparql>')  # Remove namespace
  xml = Nokogiri::XML(raw)
  def normalize(s)
    s ? s.content.sub(/^http:\/\/rdf.freebase.com\/ns\//, 'fb:') : nil
  end
  xml.xpath('//result')
end

def get_properties(entity)
  query = "SELECT DISTINCT ?property ?property_name { #{entity} ?property ?value . ?property fb:type.object.name ?property_name }"
  sparql(query).map { |r|
    property = normalize(r.xpath('binding[@name="property"]/uri')[0])
    property_name = normalize(r.xpath('binding[@name="property_name"]/literal')[0])
    {'id' => entity, 'property' => property, 'property_name' => property_name}
  }
end

def get_values(entity, property)
  query = "SELECT DISTINCT ?value ?value_name { #{entity} #{property} ?value . OPTIONAL { ?value fb:type.object.name ?value_name } }"
  sparql(query).map { |r|
    value = normalize(r.xpath('binding[@name="value"]/uri')[0])
    value = normalize(r.xpath('binding[@name="value"]/literal')[0]) if not value
    value_name = normalize(r.xpath('binding[@name="value_name"]/literal')[0])
    {'id' => value, 'name' => value_name}
  }
end

def find_paths(item1, items2)
  id1 = item1['id']
  id2 = items2[0]['id']  # TODO: expand
  #p [id1, id2]
  query = "SELECT DISTINCT ?property ?property_name { #{id1} ?property #{id2} . ?property fb:type.object.name ?property_name }"
  one_hop = sparql(query).map { |r|
    property = normalize(r.xpath('binding[@name="property"]/uri')[0])
    property_name = normalize(r.xpath('binding[@name="property_name"]/literal')[0])
    {'id' => id1, 'property' => property, 'property_name' => property_name}
  }
  query = "SELECT DISTINCT ?property1 ?property_name1 ?property2 ?property_name2 { #{id1} ?property1 ?x . ?x ?property2 #{id2} . ?property1 fb:type.object.name ?property_name1 . ?property2 fb:type.object.name ?property_name2 }"
  two_hops = sparql(query).map { |r|
    property1 = normalize(r.xpath('binding[@name="property1"]/uri')[0])
    property_name1 = normalize(r.xpath('binding[@name="property_name1"]/literal')[0])
    property2 = normalize(r.xpath('binding[@name="property2"]/uri')[0])
    property_name2 = normalize(r.xpath('binding[@name="property_name2"]/literal')[0])
    {'id' => id1, 'property' => property1 + '/' + property2, 'property_name' => property_name1 + ' / ' + property_name2}
  }
  one_hop + two_hops
end

stack = [{'id' => 'fb:en.barack_obama'}]
#stack = [{'id' => 'fb:en.tom_cruise'}]
compute_list = lambda { |item|
  if item['list']
    # Done
  elsif item['target']
    item['list'] = find_paths(item, item['target']['list'])
  elsif item['query']
    item['list'] = fbsearch(item['query'])
  elsif item['property']
    item['list'] = get_values(item['id'], item['property'])
  else
    item['list'] = get_properties(item['id'])
  end
}
push_item = lambda { |item|
  compute_list.call(item)
  stack << item
}
def render_values(values)
  lim = 3
  s = values[0...lim].map{|x| [x['id'], x['name']].compact.join("_")}.join(' | ')
  s += " (#{values.size})" if values.size > lim
  s
end
print_list = lambda { |filter|
  # Print list of items for the top
  item = stack[-1]
  compute_list.call(item)
  item['list'].each_with_index { |r,i|
    s = [i, r['property'] || r['id'], r['property_name'], r['name']].join("\t")
    s += "\t" + render_values(r['list']) if r['list']
    puts s if (not filter) || s =~ /#{filter}/
  }
}
while true
  item = stack[-1]
  print [item['query'], item['id'], item['property'], item['target'] && item['target']['list'][0]['id']].compact.join(' ') + '> '
  line = gets
  break if line == nil
  line.chomp!
  if line == ''
    # List
    print_list.call(nil)
  elsif line =~ /^\/(.+)$/
    # List with filter
    print_list.call($1)
  elsif line == '..'
    # Pop the stack
    stack.pop if stack.size > 1
    print_list.call(nil)
  elsif line == '!'
    # call list on all children
    compute_list.call(item)
    item['list'].each { |r|
      compute_list.call(r)
    }
    print_list.call(nil)
  elsif line =~ /^(-)?(\d+)$/
    # Go to a particular selection of the list
    stay = $1 
    i = Integer($2)
    compute_list.call(item)
    if i < 0 || i >= item['list'].size
      puts "Out of range (#{item['list'].size} items)"
    else
      push_item.call(item['list'][i])
      print_list.call(nil)
      stack.pop if stay
    end
  elsif line =~ /^--(.+)$/
    # Find path to target
    target = {'query' => $1}
    compute_list.call(target)
    if target['list'].size == 0
      puts 'No matches'
    else
      push_item.call({'id' => item['id'], 'target' => target})
      print_list.call(nil)
    end
  else
    # Free form search form entities
    push_item.call({'query' => line})
    print_list.call(nil)
  end
end

$mid2id.close
