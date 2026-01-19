// File: server/src/routes/tomtomDebugRoute.ts
import * as express from 'express';
import { fetchPointFlow } from '../integrations/tomtom';
const router = express.Router();

// GET /api/v1/debug/provider/point?lat=...&lon=...
router.get('/api/v1/debug/provider/point', async (req, res) => {
  const lat = Number(req.query.lat);
  const lon = Number(req.query.lon);
  if (Number.isNaN(lat) || Number.isNaN(lon)) return res.status(400).json({ ok: false, message: 'lat/lon required' });
  const data = await fetchPointFlow(lat, lon);
  res.json({ ok: true, data });
});

export default router;
