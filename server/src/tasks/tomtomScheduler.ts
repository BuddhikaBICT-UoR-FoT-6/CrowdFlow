import * as cron from 'node-cron';
import { fetchPointFlow, computeAggregatesForPoints } from '../integrations/tomtom';
// import your DB save function(s)
import { saveAggregateToDb } from '../models/aggregates'; // implement or replace with your code

// Example: how you obtain routes/points — implement per your app
// Here we assume you have a function that returns sampled lat/lon points for a routeId.
import { getAllRoutesSamplePoints } from '../models/routes'; // implement this

// Cron schedule: every minute (adjust to provider quota)
const SCHEDULE = '*/1 * * * *';

export function startTomTomScheduler() {
  cron.schedule(SCHEDULE, async () => {
    try {
      const routes = await getAllRoutesSamplePoints(); // [{ routeId, points: [{lat, lon}, ...] }, ...]
      const windowStartMs = Date.now(); // or compute aligned window start
      for (const route of routes) {
        const providerPoints = [];
        for (const p of route.points) {
          const pd = await fetchPointFlow(p.lat, p.lon);
          providerPoints.push(pd);
        }
        const aggregate = computeAggregatesForPoints(route.routeId, providerPoints, windowStartMs);
        // Persist: adapt to your Aggregate model or collection
        await saveAggregateToDb(aggregate);
      }
    } catch (err: any) {
      console.error('TomTom scheduler error', err?.message || err);
    }
  });
}