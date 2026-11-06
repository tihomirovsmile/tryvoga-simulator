#!/usr/bin/env python3
"""Optional: cache OSM/ODbL country and oblast boundary GeoJSON inside Android assets.

Run with internet access before assembling the APK. Do not scrape live alert APIs.
"""
from pathlib import Path
from urllib.request import Request, urlopen
import json
import re
import time

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
GEO = ASSETS / 'geo'
GEO.mkdir(parents=True, exist_ok=True)
BASE = 'https://cdn.jsdelivr.net/gh/darmat1/ukraine-geo-data@main/geodata/'
MAP = {
    'а':'a','б':'b','в':'v','г':'h','ґ':'g','д':'d','е':'e','є':'ye','ж':'zh',
    'з':'z','и':'y','і':'i','ї':'yi','й':'y','к':'k','л':'l','м':'m','н':'n',
    'о':'o','п':'p','р':'r','с':'s','т':'t','у':'u','ф':'f','х':'kh',
    'ц':'ts','ч':'ch','ш':'sh','щ':'shch','ь':'','ю':'yu','я':'ya'
}
def slug(text):
    text=''.join(MAP.get(c,c) for c in text.lower())
    return re.sub('_+', '_', re.sub('[^a-z0-9 ]','_',text).replace(' ','_')).strip('_')

rows=json.loads((ASSETS / 'regions_uk.json').read_text(encoding='utf-8'))
filenames=['Ukraine.geojson']
filenames += [slug(r['name'])+'.geojson' for r in rows if r['type']=='OBLAST']
print('GeoJSON documents to fetch:',len(filenames))
for name in filenames:
    dest=GEO / name
    if dest.exists() and dest.stat().st_size > 200:
        print('Cached:',name); continue
    try:
        req=Request(BASE + name,headers={'User-Agent':'TrivogaSimulator/0.3 (offline boundary cache)'})
        with urlopen(req,timeout=50) as r: data=r.read(10*1024*1024)
        parsed=json.loads(data)
        assert parsed.get('type')=='FeatureCollection' and parsed.get('features')
        dest.write_bytes(data)
        print('Downloaded:',name,len(data))
    except Exception as exc:
        print('Unavailable:',name,str(exc))
    time.sleep(0.2)
print('GeoJSON attribution: © OpenStreetMap contributors, ODbL. https://www.openstreetmap.org/copyright')
