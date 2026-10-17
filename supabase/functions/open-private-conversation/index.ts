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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
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

    let body: { recipient_username?: unknown };
    try { body = await req.json(); } catch { return reply(400, { error: "Expected a JSON body" }); }
    const username = typeof body.recipient_username === "string" ? body.recipient_username.trim() : "";
    if (!/^[a-zA-Z0-9_.-]{3,24}$/.test(username)) {
      return reply(400, { error: "The selected user needs a valid username before messaging" });
    }

    const { data: recipient, error: recipientError } = await adminClient
      .from("profiles").select("id, username").eq("username", username).maybeSingle();
    if (recipientError) return reply(500, { error: "Could not look up recipient" });
    if (!recipient) return reply(404, { error: "Registered user not found" });
    if (recipient.id === callerId) return reply(400, { error: "You cannot open a conversation with yourself" });

    // Find only a non-group conversation whose complete membership is exactly these two users.
    const { data: callerMemberships, error: callerMembershipError } = await adminClient
      .from("conversation_members").select("conversation_id").eq("user_id", callerId);
    if (callerMembershipError) return reply(500, { error: "Could not check existing conversations" });
    const ids = [...new Set((callerMemberships ?? []).map((row) => row.conversation_id as string))];

    for (let offset = 0; offset < ids.length; offset += 100) {
      const batch = ids.slice(offset, offset + 100);
      const { data: recipientMemberships, error: recipientMembershipError } = await adminClient
        .from("conversation_members").select("conversation_id")
        .eq("user_id", recipient.id).in("conversation_id", batch);
      if (recipientMembershipError) return reply(500, { error: "Could not check existing conversations" });
      const sharedIds = [...new Set((recipientMemberships ?? []).map((row) => row.conversation_id as string))];
      if (sharedIds.length === 0) continue;

      const { data: candidates, error: candidateError } = await adminClient
        .from("conversations").select("id").in("id", sharedIds).eq("is_group", false);
      if (candidateError) return reply(500, { error: "Could not load existing conversations" });

      for (const candidate of candidates ?? []) {
        const { data: members, error: membersError } = await adminClient
          .from("conversation_members").select("user_id")
          .eq("conversation_id", candidate.id);
        if (membersError) return reply(500, { error: "Could not verify private conversation membership" });
        const memberIds = new Set((members ?? []).map((row) => row.user_id as string));
        if (memberIds.size === 2 && memberIds.has(callerId) && memberIds.has(recipient.id)) {
          return reply(200, { conversation: { id: candidate.id }, reused: true });
        }
      }
    }

    const { data: conversation, error: conversationError } = await adminClient
      .from("conversations").insert({ is_group: false, title: null, created_by: callerId })
      .select("id, created_at").single();
    if (conversationError || !conversation) return reply(500, { error: "Could not create private conversation" });

    const { error: memberError } = await adminClient.from("conversation_members").insert([
      { conversation_id: conversation.id, user_id: callerId },
      { conversation_id: conversation.id, user_id: recipient.id },
    ]);
    if (memberError) {
      await adminClient.from("conversations").delete().eq("id", conversation.id);
      return reply(500, { error: "Could not establish private conversation membership" });
    }

    return reply(201, { conversation: { id: conversation.id, created_at: conversation.created_at }, reused: false });
  } catch {
    return reply(500, { error: "Private conversation request failed" });
  }
});
