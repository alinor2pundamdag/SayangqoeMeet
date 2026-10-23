import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok");
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });

  const expected = Deno.env.get("MEDIA_CLEANUP_CRON_SECRET");
  const supplied = req.headers.get("x-cleanup-secret");
  if (!expected || !supplied || supplied.length !== expected.length || supplied !== expected) {
    return json(401, { error: "Unauthorized" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return json(500, { error: "Server configuration is incomplete" });

  let body: { dry_run?: unknown };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "Expected a JSON body" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const cutoff = new Date(Date.now() - 60_000).toISOString();

  if (body.dry_run === true) {
    const { count, error } = await admin
      .from("call_sessions")
      .select("id", { count: "exact", head: true })
      .eq("status", "ringing")
      .lt("created_at", cutoff);
    if (error) {
      console.error("Stale-call dry-run failed", JSON.stringify({ code: error.code }));
      return json(500, { error: "Could not inspect stale call sessions" });
    }
    return json(200, { dry_run: true, stale_ringing_calls: count ?? 0, marked_missed: 0 });
  }

  const { data, error } = await admin
    .from("call_sessions")
    .update({ status: "missed", ended_at: new Date().toISOString() })
    .eq("status", "ringing")
    .lt("created_at", cutoff)
    .select("id");

  if (error) {
    console.error("Stale-call cleanup failed", JSON.stringify({ code: error.code }));
    return json(500, { error: "Could not close stale call sessions" });
  }
  return json(200, {
    dry_run: false,
    stale_ringing_calls: data?.length ?? 0,
    marked_missed: data?.length ?? 0,
  });
});
