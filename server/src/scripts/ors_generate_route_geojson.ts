import dotenv = require('dotenv');
import axios from 'axios';
import * as fs from 'fs';
import * as path from 'path';

// Usage (recommended):
//   ts-node src/scripts/ors_generate_route_geojson.ts --routeId 138 --profile driving-car \
//     --coords "79.8612,6.9271;79.9000,6.9500" --out "../app/src/main/assets/routes/route_138.geojson"
//
// Positional fallback (npm can sometimes forward args this way):
//   ts-node src/scripts/ors_generate_route_geojson.ts 138 driving-car "lon,lat;lon,lat" "../out.geojson"
//
// Notes:
// - Requires ORS_API_KEY in environment.
// - coords are "lon,lat;lon,lat;..." (>=2 points).
// - Produces GeoJSON FeatureCollection(LineString).

dotenv.config();

function arg(name: string): string | undefined {
  const idx = process.argv.indexOf(name);
  if (idx === -1) return undefined;
  return process.argv[idx + 1];
}

function parseCoords(raw: string): number[][] {
  const pairs = raw.split(';').map(s => s.trim()).filter(Boolean);
  if (pairs.length < 2) throw new Error('Need at least 2 coordinate pairs');
  return pairs.map(p => {
    const [lonS, latS] = p.split(',').map(x => x.trim());
    const lon = Number(lonS);
    const lat = Number(latS);
    if (!Number.isFinite(lon) || !Number.isFinite(lat)) throw new Error(`Bad coord: ${p}`);
    return [lon, lat];
  });
}

function resolveArgs(): { routeId: string; profile: string; coordsRaw: string; out: string } {
  // Preferred: flagged args
  const routeIdFlag = arg('--routeId');
  const coordsFlag = arg('--coords');
  const outFlag = arg('--out');
  const profileFlag = arg('--profile');

  if (routeIdFlag && coordsFlag && outFlag) {
    return {
      routeId: routeIdFlag,
      profile: profileFlag || 'driving-car',
      coordsRaw: coordsFlag,
      out: outFlag
    };
  }

  // Fallback: positional: <routeId> <profile?> <coords> <out>
  const pos = process.argv.slice(2).filter(a => a && !a.startsWith('-'));
  const routeId = pos[0];
  const profile = pos[1] || 'driving-car';
  const coordsRaw = pos[2];
  const out = pos[3];

  if (!routeId || !coordsRaw || !out) {
    throw new Error('Missing --routeId (or positional args)');
  }

  return { routeId, profile, coordsRaw, out };
}

async function main() {
  const apiKey = process.env.ORS_API_KEY;
  if (!apiKey) throw new Error('Missing ORS_API_KEY in environment');

  const { routeId, profile, coordsRaw, out } = resolveArgs();
  const coords = parseCoords(coordsRaw);

  const url = `https://api.openrouteservice.org/v2/directions/${profile}/geojson`;

  const resp = await axios.post(
    url,
    {
      coordinates: coords
    },
    {
      headers: {
        Authorization: apiKey,
        'Content-Type': 'application/json'
      },
      timeout: 20000
    }
  );

  // ORS already returns a FeatureCollection with a LineString + properties.
  // We wrap/normalize properties to match our asset convention.
  const geo = resp.data;

  if (!geo || geo.type !== 'FeatureCollection') {
    throw new Error('Unexpected ORS response; expected FeatureCollection');
  }

  const feature = geo.features?.[0];
  if (!feature || feature.geometry?.type !== 'LineString') {
    throw new Error('Unexpected ORS response; expected LineString feature');
  }

  feature.properties = {
    ...(feature.properties || {}),
    routeId,
    name: feature.properties?.name || `ORS route ${routeId}`,
    source: 'openrouteservice'
  };

  const outPath = path.resolve(process.cwd(), out);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(geo, null, 2), 'utf8');

  console.log(`Wrote ${outPath}`);
}

main().catch(err => {
  console.error(err?.message || err);
  process.exit(1);
});
