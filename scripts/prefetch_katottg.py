#!/usr/bin/env python3
"""Import the Ukrainian register from ua-geo (source KATOTTG), including all cities and villages.
Run with internet BEFORE APK build, or let the Android app download it on first launch.
Attribution: source Ministry of Communities and Territories Development of Ukraine via
https://github.com/alexbabintsev/ua-geo (user should verify snapshot date)."""
import csv,io,json,pathlib,urllib.request
out=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/regions_full.json'
url='https://raw.githubusercontent.com/alexbabintsev/ua-geo/main/data/out/katottg.csv'
print('Downloading KATOTTG source',url)
with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'TrivogaSimulator/0.4'}),timeout=90) as f:
    source=f.read().decode('utf-8-sig')
levels={1:'OBLAST',2:'RAYON',3:'HROMADA',4:'SETTLEMENT'}
rows=[]
for r in csv.DictReader(io.StringIO(source)):
    try: level=int(r['level'])
    except (KeyError,ValueError): continue
    if level not in levels:continue
    item={'id':r['code'],'name':r.get('full_name') or r.get('name'),'type':levels[level],
          'parentId':r.get('parent_code') or None}
    if not item['name']:continue
    for key in ('lat','lon'):
        try: item[key]=float(r[key])
        except (KeyError,ValueError,TypeError): pass
    rows.append(item)
if len(rows)<25000:raise RuntimeError('Incomplete registry, no asset overwritten')
out.write_text(json.dumps(rows,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
from collections import Counter
print('Wrote',len(rows),'records',Counter(x['type'] for x in rows),'to',out,'size',out.stat().st_size)
