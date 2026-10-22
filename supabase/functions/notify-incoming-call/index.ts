import { createClient } from "npm:@supabase/supabase-js@2";

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function reply(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}
function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}
async function firebaseAccessToken(account: { client_email: string; private_key: string; project_id: string }) {
  const now = Math.floor(Date.now() / 1000);
  const unsigned = encodeJson({ alg: "RS256", typ: "JWT" }) + "." + encodeJson({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const pem = account.private_key.replace(/\\n/g, "\n");
  const der = Uint8Array.from(atob(pem.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "")), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: unsigned + "." + base64Url(signature) }),
  });
  const data = await response.json();
  if (!response.ok || typeof data.access_token !== "string") throw new Error("Firebase token exchange failed");
  return data.access_token as string;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (req.method !== "POST") return reply(405, { error: "Method not allowed" });
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return reply(401, { error: "Sign-in required" });
  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return reply(500, { error: "Server configuration is incomplete" });
  const userClient = createClient(url, anonKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false, autoRefreshToken: false } });
  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: auth, error: authError } = await userClient.auth.getUser();
  if (authError || !auth.user) return reply(401, { error: "Session is invalid" });
  let body: { call_id?: unknown };
  try { body = await req.json(); } catch { return reply(400, { error: "Expected JSON body" }); }
  const callId = typeof body.call_id === "string" ? body.call_id.trim() : "";
  if (!callId) return reply(400, { error: "call_id is required" });
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(callId)) {
    return reply(400, { error: "call_id must be a UUID" });
  }

  const { data: call, error: callError } = await admin.from("call_sessions")
    .select("id, caller_id, callee_id, call_type, status")
    .eq("id", callId).maybeSingle();
  if (callError) return reply(500, { error: "Could not verify call session" });
  if (!call) return reply(404, { error: "Call session not found" });
  if (call.caller_id !== auth.user.id) return reply(403, { error: "Only the caller may request the initial call alert" });
  if (call.status !== "ringing") return reply(409, { error: "Call is no longer ringing" });

  const [{ data: profile }, { data: devices, error: devicesError }] = await Promise.all([
    admin.from("profiles").select("display_name, username").eq("id", auth.user.id).maybeSingle(),
    admin.from("device_tokens").select("token, user_id").eq("user_id", call.callee_id),
  ]);
  if (devicesError) return reply(500, { error: "Could not load recipient devices" });
  if (!devices?.length) return reply(200, { sent: 0, reason: "Recipient has no registered push token" });
  const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!raw) return reply(503, { error: "Firebase sender credentials are not configured" });
  let account: { client_email: string; private_key: string; project_id: string };
  try { account = JSON.parse(raw); } catch { return reply(500, { error: "Firebase sender credentials are invalid" }); }
  if (!account.client_email || !account.private_key || !account.project_id) return reply(500, { error: "Firebase sender credentials are incomplete" });

  const callerName = profile?.display_name || (profile?.username ? "@" + profile.username : "SayangqoeMeet user");
  try {
    const accessToken = await firebaseAccessToken(account);
    const results = await Promise.all(devices.map(async device => {
      const response = await fetch("https://fcm.googleapis.com/v1/projects/" + account.project_id + "/messages:send", {
        method: "POST",
        headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
        body: JSON.stringify({ message: {
          token: device.token,
          data: { type: "incoming_call", call_id: call.id, caller_name: callerName, call_type: call.call_type, recipient_id: call.callee_id },
          android: { priority: "high", ttl: "45s" },
        } }),
      });
      return response.ok;
    }));
    return reply(200, { sent: results.filter(Boolean).length, failed: results.filter(ok => !ok).length });
  } catch {
    return reply(502, { error: "Push delivery could not be completed" });
  }
});