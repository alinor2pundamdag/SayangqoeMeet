import { createClient } from "npm:@supabase/supabase-js@2";
const json=(status:number,body:Record<string,unknown>)=>new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json; charset=utf-8"}});
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return json(405,{error:"Method not allowed"});
 const expected=Deno.env.get("MEDIA_CLEANUP_CRON_SECRET"),supplied=req.headers.get("x-cleanup-secret");
 if(!expected||!supplied||supplied.length!==expected.length||supplied!==expected)return json(401,{error:"Unauthorized"});
 const url=Deno.env.get("SUPABASE_URL"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"); if(!url||!service)return json(500,{error:"Server configuration is incomplete"});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 let body:{dry_run?:unknown};
 try{body=await req.json()}catch{return json(400,{error:"Expected a JSON body"})}
 if(body.dry_run===true){
  const {count,error:queueError}=await admin.from("message_media_cleanup_queue")
   .select("message_id",{count:"exact",head:true}).in("status",["pending","claimed"]);
  if(queueError){console.error("Cleanup dry-run queue check failed",JSON.stringify({code:queueError.code}));return json(500,{error:"Could not inspect cleanup queue"})}
  return json(200,{dry_run:true,queue_rows:count??0,claimed:0,purged:0,failed:0});
 }
 const {data:jobs,error}=await admin.rpc("claim_chat_media_cleanup",{p_limit:50});
 if(error){console.error("Claim failed",JSON.stringify({code:error.code,message:error.message}));return json(500,{error:"Could not claim cleanup jobs"})}
 let purged=0,failed=0,claimed=0;
 for(const job of jobs??[]){
  claimed++; const path=typeof job.object_path==="string"?job.object_path:"";
  if(job.bucket_id!=="chat-attachments"||!path.startsWith(String(job.conversation_id)+"/")||path.startsWith("/")||path.split("/").some((p:string)=>p==="."||p==="..")||path.split("/").length<3){
   failed++;await admin.rpc("finish_chat_media_cleanup",{p_message_id:job.message_id,p_success:false,p_error:"Rejected unsafe bucket/path"});continue;
  }
  const {error:removeError}=await admin.storage.from("chat-attachments").remove([path]);
  if(removeError){failed++;console.error("Storage remove failed",JSON.stringify({message_id:job.message_id,message:removeError.message}));await admin.rpc("finish_chat_media_cleanup",{p_message_id:job.message_id,p_success:false,p_error:removeError.message});continue}
  const {data:done,error:finishError}=await admin.rpc("finish_chat_media_cleanup",{p_message_id:job.message_id,p_success:true,p_error:null});
  if(finishError||done!==true){failed++;console.error("Finish failed",JSON.stringify({message_id:job.message_id}))}else purged++;
 }
 return json(200,{claimed,purged,failed});
});