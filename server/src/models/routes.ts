// File: server/src/models/routes.ts
import mongoose from 'mongoose';

const MAX_POINTS_DEFAULT = 12;

/**
 * Return an array of { routeId, points: [{lat, lon}, ...] } for routes that have samples.
 * - windowStartMs is optional; if provided it will match samples with that windowStartMs.
 * - maxPoints controls sample size per route (defaults to 12).
 *
 * Implemented using Mongoose's native connection collection access (no MongoClient).
 */
export async function getAllRoutesSamplePoints(windowStartMs?: number, maxPoints = MAX_POINTS_DEFAULT) {
  const coll = mongoose.connection.collection('samples');
  const match: any = {};
  if (typeof windowStartMs === 'number') match.windowStartMs = windowStartMs;

  let routeIds: string[] = [];
  try {
    routeIds = (await coll.distinct('routeId', match)) as string[];
  } catch (e: unknown) {
    console.error('getAllRoutesSamplePoints: failed to list distinct routeIds', (e as any)?.message || e);
    return [];
  }

  const results: Array<{ routeId: string; points: { lat: number; lon: number }[] }> = [];

  for (const routeId of routeIds) {
    try {
      const pipeline: any[] = [
        { $match: { routeId, ...match } },
        { $sample: { size: Math.max(1, Math.min(50, maxPoints)) } },
        { $project: { _id: 0, lat: 1, lon: 1, point: 1, points: 1, segment: 1 } }
      ];

      const docs = await coll.aggregate(pipeline).toArray();

      const points: { lat: number; lon: number }[] = [];
      for (const d of docs) {
        if (d == null) continue;
        if (typeof d.lat === 'number' && typeof d.lon === 'number') {
          points.push({ lat: d.lat, lon: d.lon });
          if (points.length >= maxPoints) break;
          continue;
        }
        if (d.point && typeof d.point.lat === 'number' && typeof d.point.lon === 'number') {
          points.push({ lat: d.point.lat, lon: d.point.lon });
          if (points.length >= maxPoints) break;
          continue;
        }
        if (Array.isArray(d.points) && d.points.length > 0) {
          for (const p of d.points) {
            if (p && typeof p.lat === 'number' && typeof p.lon === 'number') {
              points.push({ lat: p.lat, lon: p.lon });
            }
            if (points.length >= maxPoints) break;
          }
          if (points.length >= maxPoints) break;
        }
        if (d.segment) {
          if (Array.isArray(d.segment)) {
            for (const seg of d.segment) {
              if (seg && typeof seg.lat === 'number' && typeof seg.lon === 'number') {
                points.push({ lat: seg.lat, lon: seg.lon });
              }
              if (points.length >= maxPoints) break;
            }
            if (points.length >= maxPoints) break;
          }
        }
        if (points.length >= maxPoints) break;
      }

      if (points.length > 0) {
        results.push({ routeId, points: points.slice(0, maxPoints) });
      }
    } catch (e: unknown) {
      console.error('getAllRoutesSamplePoints error for route', routeId, (e as any)?.message || e);
    }
  }

  return results;
}

// Optional convenience: get distinct route ids only
export async function getAllRouteIds(windowStartMs?: number) {
  const coll = mongoose.connection.collection('samples');
  const match: any = {};
  if (typeof windowStartMs === 'number') match.windowStartMs = windowStartMs;
  try {
    return await coll.distinct('routeId', match);
  } catch (e: unknown) {
    console.error('getAllRouteIds error', (e as any)?.message || e);
    return [];
  }
}
