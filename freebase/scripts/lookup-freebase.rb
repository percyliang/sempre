#!/usr/bin/ruby

require 'uri'
require 'json'

# Query freebase.com for the mid of an entity (e.g., fb:en.barack_obama)

def slash(id)
  reverse = false
  if id =~ /^!(.+)$/
    reverse = true
    id = $1
  end
  id =~ /^fb:(.+)$/ or raise "Bad: #{id}"
  (reverse ? '!' : '') + '/' + $1.gsub(/\./, '/')
end
  
def unslash(slash_id)
  slash_id =~ /^\/(.+)$/ or raise "Bad: #{slash_id}"
  'fb:' + $1.gsub(/\//, '.')
end

def process(formula)
  # (!fb:broadcast.tv_station_owner.tv_stations fb:en.nbc)
  if formula =~ /^\((!?fb:.+) (fb:.+)\)$/
    property = slash($1)
    entity = slash($2)
    query = "[{ \"id\": null, \"mid\": null, \"name\": null, \"#{property}\": { \"id\": \"#{entity}\" } }]"
  elsif formula =~ /\(\(lambda x \((!?fb:.+) \((!?fb:.+) \(var x\)\)\)\) (fb:.+)\)$/
    property1 = slash($1)
    property2 = slash($2)
    entity = slash($3)
    query = "[{ \"id\": null, \"mid\": null, \"name\": null, \"#{property1}\": { \"#{property2}\": { \"id\": \"#{entity}\" } } }]"
  elsif formula =~ /^(fb:.+)$/
    entity = slash($1)
    query = "[{ \"id\": \"#{entity}\", \"mid\": null, \"name\": null }]"
  else
    raise "Bad: #{formula}"
  end
  url = 'https://www.googleapis.com/freebase/v1/mqlread/?query=' + URI::escape(query).gsub(/\[/, '%5B').gsub(/\]/, '%5D')

  $stderr.puts query
  map = JSON::parse(`curl -s '#{url}'`)
  map['result'].each { |ent|
    mid = unslash(ent['mid'])
    id = unslash(ent['id'])
    canonical_id = `grep "^#{mid}	" /u/nlp/data/semparse/scr/freebase/freebase-rdf-2013-06-09-00-00.canonical-id-map`.split[1]
    puts [mid, id, canonical_id, ent['name']].join("\t")
  }
end

if ARGV.size == 0
  while true
    print "formula> "
    formula = gets
    if not formula
      puts
      break
    end
    process(formula)

    system "./run @mode=query @index=jackson:3090 -formula '#{formula}'"
  end
else
  ARGV.each { |formula| process(formula) }
end
