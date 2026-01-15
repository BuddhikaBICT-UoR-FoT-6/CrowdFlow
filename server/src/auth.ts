import type { Request, Response, NextFunction } from 'express';
import jwt, { sign, verify, type SignOptions } from 'jsonwebtoken';
import user, { type UserRole } from './models/User';
import crypto from 'crypto';
import { RefreshToken } from "./models/RefreshToken";

const JWT_SECRET = process.env.JWT_SECRET!;
const REFRESH_SECRET = process.env.REFRESH_SECRET!;

// JWT payload for access tokens
export type JwtUser = {
  sub: string;
  email: string;
  role: UserRole;
};

// JWT payload for refresh tokens
export type JwtRefresh = {
  sub: string;
  jti: string;
};

// Get JWT secret from environment
export function getJwtSecret(): string {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error('Missing JWT_SECRET in environment');
  }
  return secret;
}

// Sign a short-lived access token
export function signAccessToken(user: { id: string; email: string; role: UserRole }): string {
  const payload: JwtUser = { sub: user.id, email: user.email, role: user.role };
  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: (process.env.JWT_EXPIRES_IN || '7d') as SignOptions['expiresIn'],
  };
  return jwt.sign(payload, getJwtSecret(), opts);
}

// Sign a long-lived refresh token with unique jti
export function signRefreshToken(userId: string): { token: string; jti: string } {
  const jti = crypto.randomBytes(16).toString('hex');
  const payload: JwtRefresh = { sub: userId, jti };
  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: (process.env.JWT_REFRESH_EXPIRES_IN || '30d') as SignOptions['expiresIn'],
  };
  return { token: jwt.sign(payload, getJwtSecret(), opts), jti };
}

// Verify a refresh token and return its payload
export function verifyRefreshToken(token: string): JwtRefresh {
  return jwt.verify(token, getJwtSecret()) as JwtRefresh;
}

// Express middleware to require a valid access token
export type AuthedRequest = Request & { user?: JwtUser };
export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  try {
    const header = String(req.headers.authorization || '');
    const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
    if (!token) {
      return res.status(401).json({ ok: false, error: 'Missing token' });
    }
    const decoded = jwt.verify(token, getJwtSecret()) as JwtUser;
    req.user = decoded;
    next();
  } catch (e) {
    return res.status(401).json({ ok: false, error: 'Invalid token' });
  }
}

// Issue tokens after login/register
// Stores refresh token in DB for lifecycle management
function issueTokens(userId: string) {
  const jwtToken = sign({ userId }, JWT_SECRET, { expiresIn: "15m" });
  const refreshToken = sign({ userId }, REFRESH_SECRET, { expiresIn: "7d" });
  // Save refreshToken in DB
  RefreshToken.create({ userId, token: refreshToken });
  return { jwt: jwtToken, refreshToken };
}

// Refresh endpoint: exchange refresh token for new access token
export async function refreshTokenHandler(req: Request, res: Response) {
  const { token } = req.body;
  try {
    const payload = verify(token, REFRESH_SECRET) as any;
    // Check token in DB
    const exists = await RefreshToken.findOne({ userId: payload.userId, token });
    if (!exists) return res.status(401).send("Invalid refresh token");
    const newJwt = sign({ userId: payload.userId }, JWT_SECRET, { expiresIn: "15m" });
    res.json({ jwt: newJwt });
  } catch {
    res.status(401).send("Invalid refresh token");
  }
}

// On logout, delete refresh token from DB
export async function logoutHandler(req: Request, res: Response) {
  const { token } = req.body;
  await RefreshToken.deleteOne({ token });
  res.sendStatus(200);
}
