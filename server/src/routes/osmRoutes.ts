import * as express from 'express';
import { fetchBusRouteGeoJsonByRef, listBusRoutesByRef } from '../integrations/overpass';
import { listNearbyBusRoutes } from '../integrations/overpassNearby';

const router = express.Router();

// GET /api/v1/osm/routes?q=10
router.get('/api/v1/osm/routes', async (req, res) => {
  try {
    const q = String(req.query.q || '').trim();
    const limit = Number(req.query.limit || 20);
    const routes = await listBusRoutesByRef(q, Math.max(1, Math.min(50, limit)));
    return res.json({ ok: true, data: routes });
  } catch (e: any) {
    console.error('/api/v1/osm/routes error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed' });
  }
});

// GET /api/v1/osm/routes/:ref/geojson
router.get('/api/v1/osm/routes/:ref/geojson', async (req, res) => {
  try {
    const ref = String(req.params.ref || '').trim();
    const geo = await fetchBusRouteGeoJsonByRef(ref);
    return res.json({ ok: true, data: geo });
  } catch (e: any) {
    console.error('/api/v1/osm/routes/:ref/geojson error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed' });
  }
});

// GET /api/v1/osm/routes/nearby?lat=..&lon=..&radiusKm=..&limit=..
router.get('/api/v1/osm/routes/nearby', async (req, res) => {
  try {
    const lat = Number(req.query.lat);
    const lon = Number(req.query.lon);
    const radiusKm = req.query.radiusKm != null ? Number(req.query.radiusKm) : 5;
    const limit = req.query.limit != null ? Number(req.query.limit) : 20;

    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      return res.status(400).json({ ok: false, error: 'lat/lon required' });
    }

    const routes = await listNearbyBusRoutes(
      lat,
      lon,
      Number.isFinite(radiusKm) ? radiusKm : 5,
      Math.max(1, Math.min(50, Number.isFinite(limit) ? limit : 20))
    );

    return res.json({ ok: true, data: routes });
  } catch (e: any) {
    console.error('/api/v1/osm/routes/nearby error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed' });
  }
});

export default router;
