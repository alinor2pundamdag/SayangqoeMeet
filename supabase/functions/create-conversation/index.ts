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
  if (!url || !anonKey || !serviceRoleKey) return reply(500, { error: "Server configuration is incomplete" });

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
  try { body = await req.json(); } catch { return reply(400, { error: "Expected a JSON body" }); }
  const username = typeof body.recipient_username === "string" ? body.recipient_username.trim() : "";
  if (!/^[a-zA-Z0-9_.-]{3,24}$/.test(username)) {
    return reply(400, { error: "Enter a username between 3 and 24 allowed characters" });
  }

  const { data: recipient, error: recipientError } = await adminClient
    .from("profiles").select("id, username, display_name").eq("username", username).maybeSingle();
  if (recipientError) return reply(500, { error: "Could not look up recipient" });
  if (!recipient) return reply(404, { error: "No profile found for that username" });
  if (recipient.id === callerId) return reply(400, { error: "You cannot invite yourself" });

  const { data: existingInvite, error: existingInviteError } = await adminClient
    .from("conversation_invites")
    .select("id, status")
    .eq("inviter_id", callerId)
    .eq("recipient_id", recipient.id)
    .in("status", ["pending", "accepted"])
    .limit(1)
    .maybeSingle();
  if (existingInviteError) return reply(500, { error: "Could not check existing invitations" });
  if (existingInvite?.status === "pending") return reply(409, { error: "You already have a pending invitation for this person" });
  if (existingInvite?.status === "accepted") return reply(409, { error: "You already have an accepted conversation with this person" });

  const { data: conversation, error: conversationError } = await adminClient
    .from("conversations")
    .insert({ is_group: false, title: null, created_by: callerId })
    .select("id, created_at").single();
  if (conversationError || !conversation) return reply(500, { error: "Could not create conversation" });

  const { error: memberError } = await adminClient
    .from("conversation_members")
    .insert({ conversation_id: conversation.id, user_id: callerId });
  if (memberError) {
    await adminClient.from("conversations").delete().eq("id", conversation.id);
    return reply(500, { error: "Could not establish creator membership" });
  }

  const { data: invite, error: inviteError } = await adminClient
    .from("conversation_invites")
    .insert({
      conversation_id: conversation.id,
      inviter_id: callerId,
      recipient_id: recipient.id,
      status: "pending",
    })
    .select("id, status, created_at")
    .single();
  if (inviteError || !invite) {
    await adminClient.from("conversations").delete().eq("id", conversation.id);
    return reply(500, { error: "Could not create the invitation" });
  }

  return reply(201, {
    conversation: { id: conversation.id, created_at: conversation.created_at },
    invitation: { id: invite.id, status: invite.status, created_at: invite.created_at },
    recipient: { username: recipient.username, display_name: recipient.display_name },
  });
});
