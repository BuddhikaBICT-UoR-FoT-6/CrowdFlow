import axios from 'axios';

/**
 * Minimal Overpass API integration for fetching OSM bus routes.
 *
 * We query for relations:
 *   type=route
 *   route=bus
 * scoped to Sri Lanka area
 */

const OVERPASS_URL = process.env.OVERPASS_URL || 'https://overpass-api.de/api/interpreter';

// Sri Lanka (OSM admin relation id 5362). In Overpass you refer to it as area(3600000000 + relationId)
const SRI_LANKA_AREA = 3600065362;

export type OverpassElement = {
  type: 'relation' | 'way' | 'node';
  id: number;
  tags?: Record<string, string>;
  // When using `out geom`, relations/ways include geometry
  geometry?: Array<{ lat: number; lon: number }>;
};

export type OverpassJson = {
  elements: OverpassElement[];
};

export type RouteSummary = {
  ref: string;
  name?: string;
  id: number;
};

/**
 * Fetch a list of bus routes by `ref` (route number) that exist in Sri Lanka.
 * This is best-effort: OSM coverage varies.
 */
export async function listBusRoutesByRef(refQuery: string, limit = 20): Promise<RouteSummary[]> {
  const q = String(refQuery || '').trim();
  if (!q) return [];

  // If user types "10", match refs that start with 10 (100,101 etc) too.
  const overpass = `
[out:json][timeout:25];
area(${SRI_LANKA_AREA})->.sl;
(
  relation["type"="route"]["route"="bus"]["ref"~"^${escapeRegex(q)}",i](area.sl);
);
out tags ${Math.max(1, Math.min(50, limit))};
`;

  const data = await postOverpass(overpass);
  const routes: RouteSummary[] = [];
  for (const el of data.elements || []) {
    if (el.type !== 'relation') continue;
    const ref = el.tags?.ref;
    if (!ref) continue;
    routes.push({ ref, name: el.tags?.name, id: el.id });
  }

  // stable ordering: numeric first if possible
  routes.sort((a, b) => {
    const an = Number(a.ref);
    const bn = Number(b.ref);
    if (Number.isFinite(an) && Number.isFinite(bn)) return an - bn;
    return a.ref.localeCompare(b.ref);
  });

  // de-duplicate by ref
  const seen = new Set<string>();
  return routes.filter(r => (seen.has(r.ref) ? false : (seen.add(r.ref), true)));
}

/**
 * Fetch a single bus route relation by exact ref and return a simple GeoJSON LineString.
 *
 * Note: many bus routes are relation(members=ways). `out geom` on relation often includes
 * geometry only for relation itself if present; coverage varies. This endpoint is a starting point.
 */
export async function fetchBusRouteGeoJsonByRef(ref: string): Promise<any> {
  const r = String(ref || '').trim();
  if (!r) throw new Error('ref required');

  const overpass = `
[out:json][timeout:25];
area(${SRI_LANKA_AREA})->.sl;
(
  relation["type"="route"]["route"="bus"]["ref"="${escapeQuotes(r)}"](area.sl);
);
out geom;
`;

  const data = await postOverpass(overpass);
  const rel = (data.elements || []).find(e => e.type === 'relation');
  if (!rel) throw new Error('Route relation not found in OSM');

  // Best-effort: if relation has geometry, use it; otherwise return empty line.
  const coords = (rel.geometry || []).map(p => [p.lon, p.lat]);

  return {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        properties: {
          ref: rel.tags?.ref,
          name: rel.tags?.name,
          osmRelationId: rel.id
        },
        geometry: {
          type: 'LineString',
          coordinates: coords
        }
      }
    ]
  };
}

async function postOverpass(query: string): Promise<OverpassJson> {
  const resp = await axios.post(OVERPASS_URL, query, {
    headers: { 'Content-Type': 'text/plain' },
    timeout: 25_000
  });
  return resp.data as OverpassJson;
}

function escapeQuotes(s: string) {
  return s.replace(/"/g, '\\"');
}

function escapeRegex(s: string) {
  // escape characters with regex meaning
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

