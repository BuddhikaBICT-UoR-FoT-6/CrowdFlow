import * as fs from 'fs';
import * as path from 'path';

// Very small GeoJSON validator tailored for our assets.
// Usage:
//   ts-node src/scripts/validate_geojson.ts --file "../app/src/main/assets/routes/route_138.geojson"
//   ts-node src/scripts/validate_geojson.ts "../app/src/main/assets/routes/route_138.geojson"

function arg(name: string): string | undefined {
  const idx = process.argv.indexOf(name);
  if (idx === -1) return undefined;
  return process.argv[idx + 1];
}

function extractLineStringCoords(geo: any): number[][] | null {
  if (!geo) return null;
  if (geo.type === 'FeatureCollection' && Array.isArray(geo.features) && geo.features.length) {
    return extractLineStringCoords(geo.features[0]);
  }
  if (geo.type === 'Feature' && geo.geometry) {
    return extractLineStringCoords(geo.geometry);
  }
  if (geo.type === 'LineString' && Array.isArray(geo.coordinates)) {
    return geo.coordinates;
  }
  return null;
}

function resolveFileArg(): string {
  // Preferred: --file <path>
  const flagged = arg('--file');
  if (flagged) return flagged;

  // Fallback: first positional arg that isn't a flag.
  const positional = process.argv.slice(2).find(a => a && !a.startsWith('-'));
  if (positional) return positional;

  throw new Error('Missing --file');
}

function main() {
  const file = resolveFileArg();
  const p = path.resolve(process.cwd(), file);
  const raw = fs.readFileSync(p, 'utf8');
  const geo = JSON.parse(raw);

  const coords = extractLineStringCoords(geo);
  if (!coords || coords.length < 2) {
    throw new Error('GeoJSON must contain a LineString with at least 2 coords');
  }

  for (const c of coords) {
    if (!Array.isArray(c) || c.length < 2) throw new Error('Bad coordinate array');
    const lon = Number(c[0]);
    const lat = Number(c[1]);
    if (!Number.isFinite(lon) || !Number.isFinite(lat)) throw new Error('Non-numeric lon/lat');
    if (lon < -180 || lon > 180 || lat < -90 || lat > 90) throw new Error(`Out of range lon/lat: ${lon},${lat}`);
  }

  console.log('OK:', p);
  console.log('Points:', coords.length);
  console.log('Start:', coords[0]);
  console.log('End:', coords[coords.length - 1]);
}

main();
