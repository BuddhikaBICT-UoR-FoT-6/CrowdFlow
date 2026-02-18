import * as express from 'express';
import { fetchPointFlow, fetchTomTomForBbox, ProviderPointData } from '../integrations/tomtom';
const router = express.Router();

// GET /api/v1/traffic?lat=&lon=
router.get('/api/v1/traffic', async (req, res) => {
  const lat = Number(req.query.lat);
  const lon = Number(req.query.lon);
  if (Number.isNaN(lat) || Number.isNaN(lon)) return res.status(400).json({ ok: false, message: 'lat/lon required' });
  try {
    const data = await fetchPointFlow(lat, lon);
    return res.json({ ok: true, data });
  } catch (e: any) {
    console.error('/api/v1/traffic error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed to fetch traffic' });
  }
});

// POST /api/v1/traffic/bbox - body: { north, west, south, east, maxPoints? }
router.post('/api/v1/traffic/bbox', async (req, res) => {
  try {
    const { north, west, south, east, maxPoints } = req.body || {};
    if ([north, west, south, east].some(v => typeof v !== 'number')) return res.status(400).json({ ok: false, error: 'invalid bbox' });
    const data = await fetchTomTomForBbox({ north, west, south, east }, { maxPoints: Number(maxPoints || 12) });
    return res.json({ ok: true, data });
  } catch (e: any) {
    console.error('/api/v1/traffic/bbox error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Failed' });
  }
});

export default router;
