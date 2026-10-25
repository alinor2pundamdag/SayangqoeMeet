import { createClient } from "npm:@supabase/supabase-js@2";
import { readJwtAssuranceLevel } from "../_shared/jwt-assurance.ts";

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

function reply(status: number, payload: Record<string, unknown>) {
  return new Response(JSON.stringify(payload), { status, headers });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (req.method !== "POST") return reply(405, { error: "Method not allowed" });

  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return reply(401, { error: "Sign-in required" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ??
    Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !publicKey || !serviceKey) {
    console.error("Admin console service configuration is incomplete.");
    return reply(500, { error: "Admin service is not configured" });
  }

  const userClient = createClient(url, publicKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: auth, error: authError } = await userClient.auth.getUser();
  if (authError || !auth.user) return reply(401, { error: "Session is invalid" });

  // getUser verifies the token with Supabase Auth. Only after that verification
  // may the assurance claim be read; all admin-console data requires MFA AAL2.
  const accessToken = authorization.slice("Bearer ".length).trim();
  if (readJwtAssuranceLevel(accessToken) !== "aal2") {
    return reply(403, { error: "Multi-factor authentication (AAL2) is required for admin access" });
  }

  const { data: membership, error: membershipError } = await admin
    .from("admin_memberships")
    .select("role, is_active")
    .eq("user_id", auth.user.id)
    .maybeSingle();

  if (membershipError) {
    console.error("Admin membership lookup failed.");
    return reply(500, { error: "Could not verify admin access" });
  }
  if (!membership || membership.is_active !== true) {
    return reply(403, {
      error: "Admin access is not provisioned for this account. Contact the project owner.",
    });
  }

  let body: { action?: unknown };
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "Expected a JSON body" });
  }
  const action = typeof body.action === "string" ? body.action : "";
  if (action !== "overview" && action !== "audit_log") {
    return reply(400, { error: "Supported actions are overview and audit_log" });
  }

  // The initial production contract is read-only. Moderation, account suspension,
  // and other privileged writes are intentionally unavailable until their policy
  // scope, approval path, and retention rules are explicitly approved.
  const auditAction = action === "overview" ? "dashboard.read" : "audit_log.read";
  const { error: auditWriteError } = await admin.from("admin_audit_log").insert({
    actor_id: auth.user.id,
    action: auditAction,
    details: { role: membership.role },
  });
  if (auditWriteError) {
    console.error("Admin audit write failed.");
    return reply(500, { error: "Could not record the required admin audit event" });
  }

  if (action === "overview") {
    const cutoff = new Date(Date.now() - 90_000).toISOString();
    const [profiles, conversations, messages, calls, presence] = await Promise.all([
      admin.from("profiles").select("id", { count: "exact", head: true }),
      admin.from("conversations").select("id", { count: "exact", head: true }),
      admin.from("messages").select("id", { count: "exact", head: true }),
      admin.from("call_sessions").select("id", { count: "exact", head: true })
        .in("status", ["ringing", "accepted", "connecting", "connected"]),
      admin.from("user_presence").select("user_id", { count: "exact", head: true })
        .gte("last_seen_at", cutoff),
    ]);
    if (profiles.error || conversations.error || messages.error || calls.error || presence.error) {
      console.error("Admin overview query failed.");
      return reply(500, { error: "Could not load admin overview" });
    }

    return reply(200, {
      role: membership.role,
      generated_at: new Date().toISOString(),
      overview: {
        registered_profiles: profiles.count ?? 0,
        conversations: conversations.count ?? 0,
        messages: messages.count ?? 0,
        active_calls: calls.count ?? 0,
        active_users_last_90_seconds: presence.count ?? 0,
      },
      available_actions: ["overview", "audit_log"],
      privileged_writes_enabled: false,
    });
  }

  const { data: events, error: eventsError } = await admin
    .from("admin_audit_log")
    .select("id, actor_id, action, target_type, target_id, details, created_at")
    .order("created_at", { ascending: false })
    .limit(50);
  if (eventsError) {
    console.error("Admin audit log read failed.");
    return reply(500, { error: "Could not load admin audit history" });
  }

  return reply(200, {
    role: membership.role,
    generated_at: new Date().toISOString(),
    events: events ?? [],
    privileged_writes_enabled: false,
  });
});
