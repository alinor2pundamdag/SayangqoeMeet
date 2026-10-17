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
  const userClient = createClient(url, anonKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false, autoRefreshToken: false } });
  const adminClient = createClient(url, serviceRoleKey, { auth: { persistSession: false, autoRefreshToken: false } });
  try {
    const { data: authData, error: authError } = await userClient.auth.getUser();
    if (authError || !authData.user) return reply(401, { error: "Session is invalid" });
    const callerId = authData.user.id;
    let body: { target_user_id?: unknown; target_username?: unknown };
    try { body = await req.json(); } catch { return reply(400, { error: "Expected a JSON body" }); }
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
    } else return reply(400, { error: "Provide target_user_id or target_username" });
    if (callerId === targetId) return reply(400, { error: "You cannot message yourself" });
    const { data: target, error: targetError } = await adminClient.from("profiles").select("id").eq("id", targetId).maybeSingle();
    if (targetError) return reply(500, { error: "Could not verify target user" });
    if (!target) return reply(404, { error: "Registered user not found" });
    const { data: ownMemberships, error: ownError } = await adminClient.from("conversation_members").select("conversation_id").eq("user_id", callerId);
    if (ownError) return reply(500, { error: "Could not check existing conversations" });
    const ownIds = [...new Set((ownMemberships ?? []).map((row) => row.conversation_id as string))];
    for (let offset = 0; offset < ownIds.length; offset += 100) {
      const { data: targetMemberships, error } = await adminClient.from("conversation_members").select("conversation_id").eq("user_id", targetId).in("conversation_id", ownIds.slice(offset, offset + 100));
      if (error) return reply(500, { error: "Could not check existing conversations" });
      const sharedIds = [...new Set((targetMemberships ?? []).map((row) => row.conversation_id as string))];
      if (!sharedIds.length) continue;
      const { data: conversations, error: conversationError } = await adminClient.from("conversations").select("id").eq("is_group", false).in("id", sharedIds);
      if (conversationError) return reply(500, { error: "Could not load existing conversations" });
      for (const conversation of conversations ?? []) {
        const { data: members, error: membersError } = await adminClient.from("conversation_members").select("user_id").eq("conversation_id", conversation.id);
        if (membersError) return reply(500, { error: "Could not verify conversation members" });
        const ids = new Set((members ?? []).map((member) => member.user_id as string));
        if (ids.size === 2 && ids.has(callerId) && ids.has(targetId)) return reply(200, { conversation_id: conversation.id, is_new: false });
      }
    }
    const { data: conversation, error: createError } = await adminClient.from("conversations").insert({ is_group: false, title: null, created_by: callerId }).select("id").single();
    if (createError || !conversation) return reply(500, { error: "Could not create private conversation" });
    const { error: memberError } = await adminClient.from("conversation_members").insert([
      { conversation_id: conversation.id, user_id: callerId }, { conversation_id: conversation.id, user_id: targetId },
    ]);
    if (memberError) {
      await adminClient.from("conversations").delete().eq("id", conversation.id);
      return reply(500, { error: "Could not establish conversation membership" });
    }
    return reply(201, { conversation_id: conversation.id, is_new: true });
  } catch {
    return reply(500, { error: "Private conversation request failed" });
  }
});
