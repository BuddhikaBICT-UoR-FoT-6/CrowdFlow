import { Schema, model, Types } from "mongoose";

// RefreshToken schema for storing refresh tokens securely in MongoDB
// Each token is linked to a user and expires automatically after 30 days
const RefreshTokenSchema = new Schema({
    userId: { type: Types.ObjectId, ref: "users", required: true }, // Reference to user
    token: { type: String, required: true, unique: true }, // The refresh token string
    createdAt: { type: Date, default: Date.now, expires: "30d" }, // Auto-remove after 30 days
});

export const RefreshToken = model("RefreshToken", RefreshTokenSchema);
