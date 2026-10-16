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
  const { data: authData, error: authError } = await userClient.auth.getUser();
  if (authError || !authData.user) return reply(401, { error: "Session is invalid" });

  let body: { invitation_id?: unknown; accept?: unknown };
  try { body = await req.json(); } catch { return reply(400, { error: "Expected a JSON body" }); }
  const invitationId = typeof body.invitation_id === "string" ? body.invitation_id : "";
  if (!invitationId || typeof body.accept !== "boolean") {
    return reply(400, { error: "invitation_id and boolean accept are required" });
  }

  const { data: invite, error: inviteError } = await adminClient
    .from("conversation_invites")
    .select("id, conversation_id, inviter_id, recipient_id, status")
    .eq("id", invitationId)
    .eq("recipient_id", authData.user.id)
    .maybeSingle();
  if (inviteError) return reply(500, { error: "Could not load invitation" });
  if (!invite) return reply(404, { error: "Invitation not found" });
  if (invite.status !== "pending") return reply(409, { error: "Invitation has already been answered" });

  if (body.accept) {
    const { error: memberError } = await adminClient.from("conversation_members").upsert(
      { conversation_id: invite.conversation_id, user_id: authData.user.id },
      { onConflict: "conversation_id,user_id", ignoreDuplicates: true },
    );
    if (memberError) return reply(500, { error: "Could not accept conversation membership" });
  }

  const nextStatus = body.accept ? "accepted" : "declined";
  const { data: updated, error: updateError } = await adminClient
    .from("conversation_invites")
    .update({ status: nextStatus, responded_at: new Date().toISOString() })
    .eq("id", invitationId)
    .eq("recipient_id", authData.user.id)
    .eq("status", "pending")
    .select("id, conversation_id, status")
    .maybeSingle();
  if (updateError || !updated) return reply(409, { error: "Invitation could not be updated; refresh and try again" });

  return reply(200, { invitation: updated });
});
