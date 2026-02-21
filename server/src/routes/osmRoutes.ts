import * as express from 'express';
import { fetchBusRouteGeoJsonByRef, listBusRoutesByRef } from '../integrations/overpass';

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

export default router;

