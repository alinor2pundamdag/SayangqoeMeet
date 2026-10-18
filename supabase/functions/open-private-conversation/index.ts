import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

function reply(status: number, payload: Record<string, unknown>) {
  return new Response(JSON.stringify(payload), { status, headers: corsHeaders });
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { status: 200, headers: corsHeaders });
  if (req.method !== "POST") return reply(405, { error: "Method not allowed" });

  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return reply(401, { error: "Sign-in required" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceRoleKey) return reply(500, { error: "Server configuration is incomplete" });

  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const adminClient = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  try {
    const { data: authData, error: authError } = await userClient.auth.getUser();
    if (authError || !authData.user) return reply(401, { error: "Session is invalid" });

    const callerId = authData.user.id;
    let body: { target_user_id?: unknown; target_username?: unknown };
    try {
      body = await req.json();
    } catch {
      return reply(400, { error: "Expected a JSON body" });
    }

    let targetId = typeof body.target_user_id === "string" ? body.target_user_id.trim() : "";
    const username = typeof body.target_username === "string" ? body.target_username.trim() : "";

    if (targetId) {
      if (!isUuid(targetId)) return reply(400, { error: "target_user_id must be a UUID" });
    } else if (username) {
      if (!/^[A-Za-z0-9_.-]{3,24}$/.test(username)) return reply(400, { error: "target_username is invalid" });
      const { data, error } = await adminClient.from("profiles").select("id").eq("username", username).maybeSingle();
      if (error) return reply(500, { error: "Could not look up target user" });
      if (!data) return reply(404, { error: "Registered user not found" });
      targetId = data.id;
    } else {
      return reply(400, { error: "Provide target_user_id or target_username" });
    }

    if (callerId === targetId) return reply(400, { error: "You cannot message yourself" });

    // Verify the UUID against Supabase Auth first. The service-role Auth Admin
    // API is authoritative even if profile-column grants are tightened.
    const { data: authTarget, error: authTargetError } =
      await adminClient.auth.admin.getUserById(targetId);
    if (authTargetError) {
      console.error("Target Auth lookup failed:", authTargetError.message);
      return reply(500, { error: "Could not verify target account" });
    }
    if (!authTarget.user) return reply(404, { error: "Registered user not found" });

    // A direct chat is only available to users with a public directory profile.
    // Keep this query minimal; never fetch private profile fields here.
    const { data: targetProfile, error: profileError } = await adminClient
      .from("profiles")
      .select("id")
      .eq("id", targetId)
      .maybeSingle();
    if (profileError) {
      console.error("Target profile lookup failed:", JSON.stringify({
        code: profileError.code,
        message: profileError.message,
        details: profileError.details,
        hint: profileError.hint,
      }));
      return reply(500, { error: "Could not verify target profile" });
    }
    if (!targetProfile) return reply(404, { error: "Registered user profile not found" });

    // A transaction-scoped advisory lock in this RPC serializes concurrent
    // requests for the same unordered user pair; writes roll back together.
    const { data: rpcData, error: rpcError } = await adminClient.rpc("get_or_create_direct_conversation", {
      p_user_1: callerId,
      p_user_2: targetId,
    });
    if (rpcError) {
      console.error("get_or_create_direct_conversation RPC failed:", rpcError.message);
      return reply(500, { error: "Could not open private conversation" });
    }

    const row = Array.isArray(rpcData) ? rpcData[0] : rpcData;
    if (!row || typeof row.conversation_id !== "string" || typeof row.is_new !== "boolean") {
      console.error("get_or_create_direct_conversation returned an unexpected payload");
      return reply(500, { error: "Private conversation response was invalid" });
    }

    return reply(row.is_new ? 201 : 200, {
      conversation_id: row.conversation_id,
      is_new: row.is_new,
    });
  } catch (error) {
    console.error("Private conversation request failed:", error instanceof Error ? error.message : "unknown error");
    return reply(500, { error: "Private conversation request failed" });
  }
});
