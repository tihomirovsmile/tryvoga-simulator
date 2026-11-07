#!/usr/bin/env python3
"""Build compact offline vector geometry for Compose Canvas.
Source: darmat1/ukraine-geo-data (© OpenStreetMap contributors, ODbL).
The app never contacts any map provider at runtime. Accuracy: geometry is simplified to
roughly 100-250 metres; attribution and ODbL sharing requirements remain applicable.
"""
import gzip,json,math,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
GEO=ROOT/'app/src/main/assets/geo'
GEO.mkdir(parents=True,exist_ok=True)
COUNTRY=GEO/'Ukraine.geojson'
if not COUNTRY.exists():
    sys.exit('Ukraine.geojson missing: run scripts/prefetch_geojson.py before this script')
TR=str.maketrans({'а':'a','б':'b','в':'v','г':'h','ґ':'g','д':'d','е':'e','є':'ye','ж':'zh','з':'z','и':'y',
 'і':'i','ї':'yi','й':'y','к':'k','л':'l','м':'m','н':'n','о':'o','п':'p','р':'r','с':'s','т':'t','у':'u',
 'ф':'f','х':'kh','ц':'ts','ч':'ch','ш':'sh','щ':'shch','ь':'','ю':'yu','я':'ya'})
def slug(s):
    return re.sub('_+','_',re.sub(r'[^a-z0-9 ]','_',s.lower().translate(TR)).replace(' ','_')).strip('_')

def perpendicular(p,a,b):
    dx=b[0]-a[0];dy=b[1]-a[1]
    if dx==dy==0: return math.dist(p,a)
    return abs(dy*p[0]-dx*p[1]+b[0]*a[1]-b[1]*a[0])/math.sqrt(dx*dx+dy*dy)

def rdp(pts,tol):
    if len(pts)<=2: return pts
    first,last=pts[0],pts[-1]
    dist,idx=max(((perpendicular(p,first,last),i) for i,p in enumerate(pts[1:-1],1)),default=(0,0))
    if dist<=tol: return [first,last]
    return rdp(pts[:idx+1],tol)[:-1]+rdp(pts[idx:],tol)

def rings(geometry,tol=0.002):
    if geometry['type']=='Polygon': polys=[geometry['coordinates']]
    elif geometry['type']=='MultiPolygon': polys=geometry['coordinates']
    else:return []
    result=[]
    for poly in polys:
        if not poly: continue
        points=poly[0] # outer ring only
        if len(points)<4: continue
        # break a closed ring at a stable midpoint to avoid degenerating into two points
        raw=[tuple(map(float,p[:2])) for p in points[:-1]]
        middle=len(raw)//2
        simp=rdp(raw[:middle+1],tol)[:-1]+rdp(raw[middle:]+raw[:1],tol)[:-1]
        if len(simp)<3: simp=raw[::max(1,len(raw)//200)]
        # Store ~5 decimals, much better than pixel-level accuracy at typical zoom
        result.append([[round(x,5),round(y,5)] for x,y in simp])
    return result

def extract(source, oblast=None):
    raw=json.loads(source.read_text('utf-8'))
    out=[]
    for feature in raw['features']:
        props=feature['properties'];name=props.get('name:uk') or props.get('name') or ''
        geom=feature.get('geometry') or {}
        if not name or geom.get('type') not in ('Polygon','MultiPolygon'):continue
        rr=rings(geom)
        if rr:
            out.append({'n':name,'o':oblast or '', 'p':rr})
    return out

regions=extract(COUNTRY)
raions=[]
missing=[]
for r in regions:
    f=GEO/(slug(r['n'])+'.geojson')
    if f.exists():raions.extend(extract(f,r['n']))
    else:missing.append(r['n'])
if len(regions)<23 or len(raions)<100:
    sys.exit(f'Incomplete geometry: {len(regions)} oblasts, {len(raions)} raions; missing: {missing}')
content={'source':'OpenStreetMap contributors, ODbL','oblasts':regions,'raions':raions}
result=GEO/'ukraine_offline.json.gz'
with gzip.open(result,'wt',encoding='utf-8',compresslevel=9) as file:json.dump(content,file,ensure_ascii=False,separators=(',',':'))
print('Compiled offline map:',len(regions),'oblasts,',len(raions),'raions,',result.stat().st_size,'bytes')
print('Missing border files:',missing)
