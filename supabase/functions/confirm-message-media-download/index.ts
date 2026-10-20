import { createClient } from "npm:@supabase/supabase-js@2";
const headers={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info","Access-Control-Allow-Methods":"POST, OPTIONS","Content-Type":"application/json; charset=utf-8"};
const json=(status:number,body:Record<string,unknown>)=>new Response(JSON.stringify(body),{status,headers});
Deno.serve(async(req:Request)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers});
 if(req.method!=="POST")return json(405,{error:"Method not allowed"});
 const authorization=req.headers.get("Authorization"); if(!authorization?.startsWith("Bearer "))return json(401,{error:"Sign-in required"});
 const url=Deno.env.get("SUPABASE_URL"),anon=Deno.env.get("SUPABASE_ANON_KEY")??Deno.env.get("SUPABASE_PUBLISHABLE_KEY"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 if(!url||!anon||!service)return json(500,{error:"Server configuration is incomplete"});
 const userClient=createClient(url,anon,{global:{headers:{Authorization:authorization}},auth:{persistSession:false,autoRefreshToken:false}});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  const {data:auth,error:authError}=await userClient.auth.getUser(); if(authError||!auth.user)return json(401,{error:"Session is invalid"});
  let body:{message_id?:unknown}; try{body=await req.json()}catch{return json(400,{error:"Expected a JSON body"})}
  if(typeof body.message_id!=="string"||!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.message_id))return json(400,{error:"message_id must be a UUID"});
  const {data:m,error:e}=await admin.from("messages").select("id,conversation_id,sender_id,attachment_path,attachment_purged_at").eq("id",body.message_id).maybeSingle();
  if(e)return json(500,{error:"Could not inspect attachment"}); if(!m||!m.attachment_path||m.attachment_purged_at)return json(404,{error:"Attachment unavailable"});
  if(m.sender_id===auth.user.id)return json(400,{error:"Sender is not a download recipient"});
  const {data:member,error:me}=await admin.from("conversation_members").select("user_id").eq("conversation_id",m.conversation_id).eq("user_id",auth.user.id).maybeSingle();
  if(me)return json(500,{error:"Could not verify membership"}); if(!member)return json(403,{error:"Not a member of this conversation"});
  const {data:confirmedAt,error:ce}=await admin.rpc("confirm_message_media_download",{p_message_id:body.message_id,p_recipient_id:auth.user.id});
  if(ce){console.error("Confirmation RPC failed",JSON.stringify({code:ce.code,message:ce.message}));return json(409,{error:"Could not confirm download"})}
  return json(200,{message_id:body.message_id,confirmed_at:confirmedAt});
 }catch(err){console.error("Confirmation failed",err instanceof Error?err.message:"unknown");return json(500,{error:"Download confirmation failed"})}
});