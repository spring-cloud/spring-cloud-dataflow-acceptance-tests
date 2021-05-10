import json
import os
import re

def maskConfiguration(config):
   maskWords=['secret','password','credential','user']
   maskedConfig = {}
   ip = re.compile("\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}.*")
   for key in config:
       val = config[key]
       for word in maskWords:
          if re.search(word, key, re.IGNORECASE):
             val='******'

       if isinstance(val, str) and ip.match(val):
            val='******'
       maskedConfig[key] = val
   return json.dumps(maskedConfig)



dataflow_tile_configuration = os.getenv("DATAFLOW_TILE_CONFIGURATION", "")
config={}
if len(dataflow_tile_configuration):
    dataflow_tile_configuration
    config = json.loads(dataflow_tile_configuration)
print("using tile configuration: %s" % (maskConfiguration(config)))

