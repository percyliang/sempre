#!/usr/bin/python

import sys
import json

class LexicalEntry:
  def __init__(self, l, f, t):
    self.lexeme=l.strip()
    self.formula=f.strip()
    self.type=t.strip()

out = open(sys.argv[2],'w')

with open(sys.argv[1]) as f:
  for line in f:
    tokens = line.split("\t")
    if len(tokens) > 2:
      continue
    if(tokens[0] == "loc_city"):
      index = tokens[1].rfind('.')
      citystate = tokens[1][index+1:]
      city = citystate[0:citystate.rfind('_')]
      city = city.replace('_',' ').strip()
      entry = LexicalEntry(city, tokens[1], "fb:en.city")
      out.write(json.dumps(entry.__dict__)+'\n')
    elif (tokens[0] == "loc_state"):
      index = tokens[1].rfind('.')
      state = tokens[1][index+1:].strip()
      state = state.replace('_',' ').strip()
      entry = LexicalEntry(state, tokens[1], "fb:en.state")
      out.write(json.dumps(entry.__dict__)+'\n')
    elif tokens[0] == "loc_river":
      index = tokens[1].rfind('.')
      river = tokens[1][index+1:].strip()
      river = river.replace('_',' ').strip()
      entry = LexicalEntry(river+" river", tokens[1], "fb:en.river")
      out.write(json.dumps(entry.__dict__)+'\n')
    elif (tokens[0] == "loc_place"):
      index = tokens[1].rfind('.')
      place = tokens[1][index+1:].strip()
      place = place.replace('_',' ').strip()
      entry = LexicalEntry(place, tokens[1], "fb:en.place")
      out.write(json.dumps(entry.__dict__)+'\n')
    elif (tokens[0] == "loc_lake"):
      index = tokens[1].rfind('.')
      lake = tokens[1][index+1:].strip()
      lake = lake.replace('_',' ').strip()
      if not 'lake' in lake:
        lake = lake + " lake"
      entry = LexicalEntry(lake, tokens[1], "fb:en.lake")
      out.write(json.dumps(entry.__dict__)+'\n')
    elif (tokens[0] == "loc_mountain"):
      index = tokens[1].rfind('.')
      mountain = tokens[1][index+1:].strip()
      mountain = mountain.replace('_',' ').strip()
      entry = LexicalEntry("mount " + mountain, tokens[1], "fb:en.mountain")
      out.write(json.dumps(entry.__dict__)+'\n')
    elif (tokens[0] == "loc_country"):
      index = tokens[1].rfind('.')
      country = tokens[1][index+1:].strip()
      country = country.replace('_',' ').strip()
      entry = LexicalEntry(country, tokens[1], "fb:en.country")
      out.write(json.dumps(entry.__dict__)+'\n')


out.close()


