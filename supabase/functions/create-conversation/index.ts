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

function logFailure(stage: string, error: unknown, requestId: string) {
  const err = error instanceof Error ? error : new Error(String(error));
  // Never log authorization headers, access tokens, or service-role credentials.
  console.error(JSON.stringify({
    event: "create_conversation_failed",
    stage,
    request_id: requestId,
    error_name: err.name,
    error_message: err.message.slice(0, 300),
  }));
}

Deno.serve(async (req: Request) => {
  const requestId = crypto.randomUUID();
  let stage = "request";
  try {
    if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
    if (req.method !== "POST") return reply(405, { error: "Method not allowed", request_id: requestId });
    const authorization = req.headers.get("Authorization");
    if (!authorization?.startsWith("Bearer ")) return reply(401, { error: "Sign-in required", request_id: requestId });

    stage = "configuration";
    const url = Deno.env.get("SUPABASE_URL");
    // Support current publishable-key environment and legacy anon-key environment.
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !anonKey || !serviceRoleKey) {
      console.error(JSON.stringify({
        event: "create_conversation_configuration_missing",
        request_id: requestId,
        has_url: Boolean(url),
        has_anon_or_publishable_key: Boolean(anonKey),
        has_service_role_key: Boolean(serviceRoleKey),
      }));
      return reply(500, { error: "Server configuration is incomplete", request_id: requestId });
    }

    stage = "authenticate";
    const userClient = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const adminClient = createClient(url, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: userResult, error: authError } = await userClient.auth.getUser();
    if (authError || !userResult.user) {
      console.warn(JSON.stringify({ event: "create_conversation_auth_rejected", request_id: requestId, error: authError?.message?.slice(0, 200) }));
      return reply(401, { error: "Session is invalid", request_id: requestId });
    }
    const callerId = userResult.user.id;

    stage = "parse_body";
    let body: { recipient_username?: unknown };
    try { body = await req.json(); } catch { return reply(400, { error: "Expected a JSON body", request_id: requestId }); }
    const username = typeof body.recipient_username === "string" ? body.recipient_username.trim() : "";
    if (!/^[a-zA-Z0-9_.-]{3,24}$/.test(username)) {
      return reply(400, { error: "Enter a username between 3 and 24 allowed characters", request_id: requestId });
    }

    stage = "recipient_lookup";
    const { data: recipient, error: recipientError } = await adminClient
      .from("profiles").select("id, username, display_name").eq("username", username).maybeSingle();
    if (recipientError) {
      logFailure(stage, recipientError, requestId);
      return reply(500, { error: "Could not look up recipient", request_id: requestId });
    }
    if (!recipient) return reply(404, { error: "No profile found for that username", request_id: requestId });
    if (recipient.id === callerId) return reply(400, { error: "You cannot invite yourself", request_id: requestId });

    stage = "existing_invite_check";
    const { data: existingInvite, error: existingInviteError } = await adminClient
      .from("conversation_invites")
      .select("id, status")
      .eq("inviter_id", callerId)
      .eq("recipient_id", recipient.id)
      .in("status", ["pending", "accepted"])
      .limit(1)
      .maybeSingle();
    if (existingInviteError) {
      logFailure(stage, existingInviteError, requestId);
      return reply(500, { error: "Could not check existing invitations", request_id: requestId });
    }
    if (existingInvite?.status === "pending") return reply(409, { error: "You already have a pending invitation for this person", request_id: requestId });
    if (existingInvite?.status === "accepted") return reply(409, { error: "You already have an accepted conversation with this person", request_id: requestId });

    stage = "conversation_insert";
    const { data: conversation, error: conversationError } = await adminClient
      .from("conversations").insert({ is_group: false, title: null, created_by: callerId })
      .select("id, created_at").single();
    if (conversationError || !conversation) {
      logFailure(stage, conversationError ?? new Error("Insert returned no conversation"), requestId);
      return reply(500, { error: "Could not create conversation", request_id: requestId });
    }

    stage = "creator_membership_insert";
    const { error: memberError } = await adminClient
      .from("conversation_members").insert({ conversation_id: conversation.id, user_id: callerId });
    if (memberError) {
      logFailure(stage, memberError, requestId);
      await adminClient.from("conversations").delete().eq("id", conversation.id);
      return reply(500, { error: "Could not establish creator membership", request_id: requestId });
    }

    stage = "invitation_insert";
    const { data: invite, error: inviteError } = await adminClient
      .from("conversation_invites").insert({
        conversation_id: conversation.id,
        inviter_id: callerId,
        recipient_id: recipient.id,
        status: "pending",
      }).select("id, status, created_at").single();
    if (inviteError || !invite) {
      logFailure(stage, inviteError ?? new Error("Insert returned no invitation"), requestId);
      await adminClient.from("conversations").delete().eq("id", conversation.id);
      return reply(500, { error: "Could not create the invitation", request_id: requestId });
    }

    console.info(JSON.stringify({ event: "create_conversation_succeeded", request_id: requestId, caller_id: callerId, recipient_id: recipient.id, conversation_id: conversation.id }));
    return reply(201, {
      conversation: { id: conversation.id, created_at: conversation.created_at },
      invitation: { id: invite.id, status: invite.status, created_at: invite.created_at },
      recipient: { username: recipient.username, display_name: recipient.display_name },
      request_id: requestId,
    });
  } catch (error) {
    logFailure(stage, error, requestId);
    return reply(500, { error: "Conversation request failed", request_id: requestId });
  }
});