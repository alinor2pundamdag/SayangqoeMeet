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
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceRoleKey) {
    return reply(500, { error: "Server configuration is incomplete" });
  }

  const userClient = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const adminClient = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: userResult, error: authError } = await userClient.auth.getUser();
  if (authError || !userResult.user) return reply(401, { error: "Session is invalid" });
  const callerId = userResult.user.id;

  let body: { recipient_username?: unknown };
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "Expected a JSON body" });
  }

  const username = typeof body.recipient_username === "string" ? body.recipient_username.trim() : "";
  if (!/^[a-zA-Z0-9_.-]{3,24}$/.test(username)) {
    return reply(400, { error: "Enter a username between 3 and 24 allowed characters" });
  }

  const { data: recipient, error: recipientError } = await adminClient
    .from("profiles")
    .select("id, username, display_name")
    .eq("username", username)
    .maybeSingle();

  if (recipientError) return reply(500, { error: "Could not look up recipient" });
  if (!recipient) return reply(404, { error: "No profile found for that username" });
  if (recipient.id === callerId) return reply(400, { error: "You cannot start a conversation with yourself" });

  const { data: conversation, error: conversationError } = await adminClient
    .from("conversations")
    .insert({ is_group: false, title: null, created_by: callerId })
    .select("id, created_at")
    .single();

  if (conversationError || !conversation) return reply(500, { error: "Could not create conversation" });

  const { error: membersError } = await adminClient.from("conversation_members").insert([
    { conversation_id: conversation.id, user_id: callerId },
    { conversation_id: conversation.id, user_id: recipient.id },
  ]);

  if (membersError) {
    await adminClient.from("conversations").delete().eq("id", conversation.id);
    return reply(500, { error: "Could not establish conversation membership" });
  }

  return reply(201, {
    conversation: { id: conversation.id, created_at: conversation.created_at },
    recipient: { username: recipient.username, display_name: recipient.display_name },
  });
});
