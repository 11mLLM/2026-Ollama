import express from "express";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const port = Number(process.env.PORT || 5173);

const publicKey = (process.env.PUBLIC_KEY || process.env.VITE_PUBLIC_KEY || "").trim();
const apiBaseUrl = (
  process.env.API_BASE_URL ||
  process.env.BACKEND_INTERNAL_URL ||
  "http://localhost:8080"
).trim();

app.get("/config.js", (req, res) => {
  res.type("application/javascript");
  res.send(
    `window.__CONFIG__ = ${JSON.stringify({
      API_BASE_URL: apiBaseUrl,
      GATE_ENABLED: Boolean(publicKey)
    })};`
  );
});

app.get("/health", (req, res) => {
  res.json({ status: "UP" });
});

app.use((req, res, next) => {
  if (!publicKey) return next();
  if (req.path.startsWith("/assets/")) return next();
  if (req.path === "/config.js") return next();
  if (req.path === "/favicon.ico") return next();

  const k = req.query.k;
  if (k !== publicKey) {
    res.status(401).type("text/plain").send("missing_or_invalid_key");
    return;
  }
  next();
});

const distDir = path.join(__dirname, "dist");
app.use(express.static(distDir));

app.get("*", (req, res) => {
  res.sendFile(path.join(distDir, "index.html"));
});

app.listen(port, "0.0.0.0");
