import axios from 'axios';

const OVERPASS_URL = process.env.OVERPASS_URL || 'https://overpass-api.de/api/interpreter';
const SRI_LANKA_AREA = 3600065362;

export type NearbyRoute = {
  ref: string;
  name?: string;
  id: number;
  distanceKm?: number;
};

/**
 * Best-effort: find bus route relations near a point by searching within a bounding box.
 *
 * Note: Overpass has `around:` which works for nodes/ways, but relations are trickier.
 * We use: relations within area + exercise a bbox constraint; results may vary.
 */
export async function listNearbyBusRoutes(lat: number, lon: number, radiusKm = 5, limit = 20): Promise<NearbyRoute[]> {
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return [];

  const d = Math.max(0.5, Math.min(25, radiusKm));
  const bbox = bboxFromPoint(lat, lon, d);

  const overpass = `
[out:json][timeout:25];
area(${SRI_LANKA_AREA})->.sl;
(
  // Relations in Sri Lanka that have member ways inside the bbox (best-effort)
  relation["type"="route"]["route"="bus"](area.sl);
);
// constrain by bbox at output time
out tags ${Math.max(1, Math.min(50, limit))} (${bbox.south},${bbox.west},${bbox.north},${bbox.east});
`;

  const resp = await axios.post(OVERPASS_URL, overpass, { headers: { 'Content-Type': 'text/plain' }, timeout: 25_000 });
  const json = resp.data as { elements?: Array<{ type: string; id: number; tags?: Record<string, string> }> };

  const out: NearbyRoute[] = [];
  for (const el of json.elements || []) {
    if (el.type !== 'relation') continue;
    const ref = el.tags?.ref;
    if (!ref) continue;
    out.push({ ref, name: el.tags?.name, id: el.id });
  }

  // De-dupe by ref, numeric sort
  const dedup = new Map<string, NearbyRoute>();
  for (const r of out) if (!dedup.has(r.ref)) dedup.set(r.ref, r);
  const list = Array.from(dedup.values());

  list.sort((a, b) => {
    const an = Number(a.ref);
    const bn = Number(b.ref);
    if (Number.isFinite(an) && Number.isFinite(bn)) return an - bn;
    return a.ref.localeCompare(b.ref);
  });

  return list.slice(0, Math.max(1, Math.min(50, limit)));
}

function bboxFromPoint(lat: number, lon: number, radiusKm: number) {
  // Very rough conversion: 1 deg lat ~ 111km; lon scales by cos(lat)
  const dLat = radiusKm / 111.0;
  const dLon = radiusKm / (111.0 * Math.cos((lat * Math.PI) / 180.0));
  return {
    north: lat + dLat,
    south: lat - dLat,
    east: lon + dLon,
    west: lon - dLon,
  };
}

