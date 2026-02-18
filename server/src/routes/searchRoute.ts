import * as express from 'express';
import axios from 'axios';
const router = express.Router();
const TOMTOM_KEY = process.env.TOMTOM_API_KEY;

// GET /api/v1/search?q=
router.get('/api/v1/search', async (req, res) => {
  try {
    const q = String(req.query.q || '').trim();
    if (!q) return res.status(400).json({ ok: false, error: 'q required' });
    const url = `https://api.tomtom.com/search/2/search/${encodeURIComponent(q)}.json`;
    const params: any = { key: TOMTOM_KEY, limit: 10 };
    const r = await axios.get(url, { params, timeout: 5000 });
    return res.json({ ok: true, data: r.data });
  } catch (e: any) {
    console.error('/api/v1/search error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Search failed' });
  }
});

// GET /api/v1/reverse?lat=&lon=
router.get('/api/v1/reverse', async (req, res) => {
  try {
    const lat = Number(req.query.lat);
    const lon = Number(req.query.lon);
    if (Number.isNaN(lat) || Number.isNaN(lon)) return res.status(400).json({ ok: false, message: 'lat/lon required' });
    const url = `https://api.tomtom.com/search/2/reverseGeocode/${lat},${lon}.json`;
    const params: any = { key: TOMTOM_KEY, limit: 1 };
    const r = await axios.get(url, { params, timeout: 5000 });
    return res.json({ ok: true, data: r.data });
  } catch (e: any) {
    console.error('/api/v1/reverse error', e?.message || e);
    return res.status(500).json({ ok: false, error: e?.message || 'Reverse geocode failed' });
  }
});

export default router;
