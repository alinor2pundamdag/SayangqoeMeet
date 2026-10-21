import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function reply(status: number, payload: Record<string, unknown>) {
  return new Response(JSON.stringify(payload), { status, headers: corsHeaders });
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

async function getFirebaseAccessToken(serviceAccount: {
  client_email: string;
  private_key: string;
  project_id: string;
}): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = encodeJson({ alg: "RS256", typ: "JWT" });
  const claims = encodeJson({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  });
  const unsigned = header + "." + claims;
  const pem = serviceAccount.private_key.replace(/\\n/g, "\n");
  const base64 = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const der = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const assertion = unsigned + "." + base64Url(signature);
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const tokenJson = await tokenResponse.json();
  if (!tokenResponse.ok || typeof tokenJson.access_token !== "string") {
    throw new Error("Firebase OAuth token exchange failed");
  }
  return tokenJson.access_token;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return reply(405, { error: "Method not allowed" });
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return reply(401, { error: "Sign-in required" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceRoleKey) return reply(500, { error: "Server configuration is incomplete" });

  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const adminClient = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: authData, error: authError } = await userClient.auth.getUser();
  if (authError || !authData.user) return reply(401, { error: "Session is invalid" });

  let body: { conversation_id?: unknown; message_id?: unknown };
  try { body = await req.json(); } catch { return reply(400, { error: "Expected a JSON body" }); }
  const conversationId = typeof body.conversation_id === "string" ? body.conversation_id : "";
  const messageId = typeof body.message_id === "string" ? body.message_id : "";
  if (!conversationId || !messageId) return reply(400, { error: "conversation_id and message_id are required" });

  const { data: message, error: messageError } = await adminClient
    .from("messages")
    .select("id, conversation_id, sender_id")
    .eq("id", messageId)
    .eq("conversation_id", conversationId)
    .eq("sender_id", authData.user.id)
    .maybeSingle();
  if (messageError) return reply(500, { error: "Could not verify message" });
  if (!message) return reply(403, { error: "Message does not belong to this caller" });

  const { data: memberships, error: membersError } = await adminClient
    .from("conversation_members")
    .select("user_id")
    .eq("conversation_id", conversationId)
    .neq("user_id", authData.user.id);
  if (membersError) return reply(500, { error: "Could not resolve recipients" });
  const recipientIds = [...new Set((memberships ?? []).map((row) => row.user_id as string))];
  if (recipientIds.length === 0) return reply(200, { sent: 0, reason: "No other conversation members" });

  const { data: devices, error: devicesError } = await adminClient
    .from("device_tokens")
    .select("token, user_id")
    .in("user_id", recipientIds);
  if (devicesError) return reply(500, { error: "Could not load recipient devices" });
  if (!devices?.length) return reply(200, { sent: 0, reason: "Recipients have no registered devices" });

  const rawServiceAccount = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!rawServiceAccount) return reply(503, { error: "Firebase sender credentials are not configured" });
  let serviceAccount: { client_email: string; private_key: string; project_id: string };
  try { serviceAccount = JSON.parse(rawServiceAccount); } catch { return reply(500, { error: "Firebase sender credentials are invalid" }); }
  if (!serviceAccount.client_email || !serviceAccount.private_key || !serviceAccount.project_id) {
    return reply(500, { error: "Firebase sender credentials are incomplete" });
  }

  try {
    const accessToken = await getFirebaseAccessToken(serviceAccount);
    const results = await Promise.all((devices ?? []).map(async (device) => {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            message: {
              token: device.token,
              // Data-only payload lets the Android client verify recipient_id before
              // displaying anything; notification payloads can bypass onMessageReceived.
              data: { type: "message", conversation_id: conversationId, message_id: messageId, recipient_id: device.user_id },
              android: { notification: { channel_id: "sayangqoemeet_messages", sound: "default" } },
            },
          }),
        },
      );
      return response.ok;
    }));
    return reply(200, { sent: results.filter(Boolean).length, failed: results.filter((ok) => !ok).length });
  } catch {
    return reply(502, { error: "Push delivery could not be completed" });
  }
});
