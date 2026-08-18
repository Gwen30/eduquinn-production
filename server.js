const http=require('http');
const fs=require('fs');
const path=require('path');
const https=require('https');
const crypto=require('crypto');
const zlib=require('zlib');
const bcrypt=require('bcryptjs');
const db=require('./db');
const PORT=Number(process.env.PORT||8080);
const PUBLIC=path.join(__dirname,'public');
const SUPPORT_EMAIL=process.env.SUPPORT_EMAIL||'support@eduquinn.co.zw';
const SUPPORT_FROM_EMAIL=process.env.SUPPORT_FROM_EMAIL||SUPPORT_EMAIL;
const BREVO_API_KEY=process.env.BREVO_API_KEY||'';
const SESSION_DAYS=Math.max(1,Math.min(90,Number(process.env.SESSION_DAYS||14)));
const MEDIA_ROOT=path.resolve(process.env.MEDIA_ROOT||path.join(__dirname,'storage','media'));
const NODE_ENV=process.env.NODE_ENV||'development';
const APP_BASE_URL=String(process.env.APP_BASE_URL||'').replace(/\/$/,'');
const ALLOWED_ORIGINS=new Set(String(process.env.ALLOWED_ORIGINS||APP_BASE_URL).split(',').map(x=>x.trim()).filter(Boolean));
const API_RATE_LIMIT=Math.max(30,Number(process.env.API_RATE_LIMIT||300));
const LOGIN_RATE_LIMIT=Math.max(3,Number(process.env.LOGIN_RATE_LIMIT||10));
const RATE_WINDOW_MS=Math.max(60000,Number(process.env.RATE_WINDOW_MS||300000));
const TRUST_PROXY=/^(1|true|yes)$/i.test(process.env.TRUST_PROXY||'');
fs.mkdirSync(MEDIA_ROOT,{recursive:true});
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'application/javascript; charset=utf-8','.json':'application/json; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.svg':'image/svg+xml','.txt':'text/plain; charset=utf-8','.pdf':'application/pdf','.webmanifest':'application/manifest+json; charset=utf-8'};
const securityHeaders={
  'X-Content-Type-Options':'nosniff','X-Frame-Options':'SAMEORIGIN','Referrer-Policy':'strict-origin-when-cross-origin',
  'Permissions-Policy':'camera=(self), microphone=(self), geolocation=()',
  'Cross-Origin-Opener-Policy':'same-origin','Cross-Origin-Resource-Policy':'same-origin',
  'Content-Security-Policy':"default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'self'; base-uri 'self'; form-action 'self'"
};
const rateBuckets=new Map();
function clientIp(req){const direct=req.socket?.remoteAddress||'unknown';if(!TRUST_PROXY)return direct;const x=String(req.headers['x-forwarded-for']||'').split(',')[0].trim();return x||direct}
function requestId(req){return String(req.headers['x-request-id']||crypto.randomUUID()).slice(0,100)}
function securityFor(req){const h={...securityHeaders};if(NODE_ENV==='production')h['Strict-Transport-Security']='max-age=31536000; includeSubDomains';return h}
function sameOriginAllowed(req){if(!['POST','PUT','PATCH','DELETE'].includes(req.method||''))return true;const origin=String(req.headers.origin||'');if(!origin)return true;if(ALLOWED_ORIGINS.size)return ALLOWED_ORIGINS.has(origin);try{const host=String(req.headers.host||'');return new URL(origin).host===host}catch{return false}}
function hitRate(key,limit,windowMs=RATE_WINDOW_MS){const now=Date.now();let b=rateBuckets.get(key);if(!b||now-b.start>=windowMs){b={start:now,count:0};rateBuckets.set(key,b)}b.count++;return {ok:b.count<=limit,retry:Math.max(1,Math.ceil((windowMs-(now-b.start))/1000)),remaining:Math.max(0,limit-b.count)}}
setInterval(()=>{const now=Date.now();for(const [k,b] of rateBuckets)if(now-b.start>RATE_WINDOW_MS*2)rateBuckets.delete(k)},RATE_WINDOW_MS).unref();
async function securityEvent(req,eventType,userId=null,details={}){try{await db.query('INSERT INTO security_events(id,user_id,event_type,ip_address,user_agent,details) VALUES($1,$2,$3,$4,$5,$6::jsonb)',[uuid(),userId,eventType,clientIp(req),String(req.headers['user-agent']||'').slice(0,500),JSON.stringify(details)])}catch{}}
function requireApiGuard(req,res,url){if(!sameOriginAllowed(req)){json(res,403,{message:'Request origin is not allowed.'});return false}const group=url.pathname==='/api/auth/login'?'login':'api';const limit=group==='login'?LOGIN_RATE_LIMIT:API_RATE_LIMIT;const r=hitRate(`${group}:${clientIp(req)}`,limit);if(!r.ok){res.setHeader('Retry-After',String(r.retry));json(res,429,{message:'Too many requests. Please try again shortly.',retryAfter:r.retry});return false}return true}
function uuid(){return crypto.randomUUID()}
function sha(v){return crypto.createHash('sha256').update(v).digest('hex')}
function cookieMap(req){return Object.fromEntries(String(req.headers.cookie||'').split(';').map(x=>x.trim()).filter(Boolean).map(x=>{const i=x.indexOf('=');return [decodeURIComponent(i<0?x:x.slice(0,i)),decodeURIComponent(i<0?'':x.slice(i+1))]}))}
function setSessionCookie(res,token,maxAge){const secure=process.env.NODE_ENV==='production'?'; Secure':'';res.setHeader('Set-Cookie',`eq_session=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${secure}`)}
function clearSessionCookie(res){const secure=process.env.NODE_ENV==='production'?'; Secure':'';res.setHeader('Set-Cookie',`eq_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secure}`)}
function json(res,status,data,extra={}){res.writeHead(status,{...securityHeaders,...(NODE_ENV==='production'?{'Strict-Transport-Security':'max-age=31536000; includeSubDomains'}:{}),'Content-Type':'application/json; charset=utf-8',...extra});res.end(JSON.stringify(data))}
function readJson(req,limit=100000){return new Promise((resolve,reject)=>{let body='';req.on('data',c=>{body+=c;if(body.length>limit){reject(Object.assign(new Error('Payload too large'),{status:413}));req.destroy()}});req.on('end',()=>{try{resolve(JSON.parse(body||'{}'))}catch{reject(Object.assign(new Error('Invalid JSON'),{status:400}))}});req.on('error',reject)})}
function publicUser(u){return u?{id:u.id,name:u.name,email:u.email,role:u.role,status:u.status,expertise:u.expertise||'',emailVerified:!!u.email_verified}:null}
async function currentUser(req){const token=cookieMap(req).eq_session;if(!token)return null;const r=await db.query(`SELECT u.*,s.id session_id FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=$1 AND s.expires_at>now() AND u.status='active'`,[sha(token)]);if(!r.rows[0])return null;db.query('UPDATE sessions SET last_seen_at=now() WHERE id=$1',[r.rows[0].session_id]).catch(()=>{});return r.rows[0]}
async function requireRole(req,res,roles){let user;try{user=await currentUser(req)}catch(e){return dbFail(res,e)}if(!user){json(res,401,{message:'Please sign in to continue.'});return null}if(!roles.includes(user.role)){json(res,403,{message:'You do not have permission to perform this action.'});return null}return user}
function dbFail(res,e){json(res,e.code==='DB_NOT_CONFIGURED'?503:500,{message:e.code==='DB_NOT_CONFIGURED'?'The database is not configured. Set DATABASE_URL.':'A database operation failed.',code:e.code||'DB_ERROR'})}
function validEmail(v){return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v||''))}
function slugify(v){return String(v||'course').toLowerCase().trim().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,180)||'course'}
async function audit(user,action,entityType=null,entityId=null,details={}){try{await db.query('INSERT INTO audit_log(id,actor_user_id,action,entity_type,entity_id,details) VALUES($1,$2,$3,$4,$5,$6::jsonb)',[uuid(),user?.id||null,action,entityType,entityId,JSON.stringify(details)])}catch{}}
async function sendBrevoEmail({userId=null,email,name='',subject,text,template='general'}){
  if(!BREVO_API_KEY||!validEmail(email))return {status:'not_configured'};
  const payload=JSON.stringify({sender:{name:'EduQuinn',email:SUPPORT_FROM_EMAIL},to:[{email,name:name||undefined}],subject,textContent:text});
  let status=0,body='';
  await new Promise(resolve=>{const r=https.request({hostname:'api.brevo.com',path:'/v3/smtp/email',method:'POST',headers:{'api-key':BREVO_API_KEY,'Content-Type':'application/json','Content-Length':Buffer.byteLength(payload)}},br=>{status=br.statusCode||0;br.on('data',c=>body+=c);br.on('end',resolve)});r.on('error',e=>{body=e.message;resolve()});r.write(payload);r.end()});
  const ok=status>=200&&status<300;try{await db.query('INSERT INTO email_delivery_log(id,user_id,email,template,subject,status,provider_reference,error_message) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',[uuid(),userId,email,template,subject,ok?'sent':'failed',ok?body.slice(0,200):null,ok?null:body.slice(0,1000)])}catch{}
  return {status:ok?'sent':'failed',code:status};
}
async function notificationPrefs(userId){const r=await db.query('SELECT * FROM notification_preferences WHERE user_id=$1',[userId]);return r.rows[0]||{in_app_messages:true,email_messages:true,in_app_announcements:true,email_announcements:true,in_app_live_classes:true,email_live_classes:true}}
async function notifyUser(userId,{type='general',title,body='',link='',emailSubject='',emailText='',channel='messages'}){
  const u=(await db.query('SELECT id,name,email FROM users WHERE id=$1',[userId])).rows[0];if(!u)return;
  const prefs=await notificationPrefs(userId),inKey='in_app_'+channel,emailKey='email_'+channel;
  if(prefs[inKey]!==false)await db.query('INSERT INTO notifications(id,user_id,type,title,body,link) VALUES($1,$2,$3,$4,$5,$6)',[uuid(),userId,type,title,body,link||null]);
  if(emailSubject&&prefs[emailKey]!==false)await sendBrevoEmail({userId,email:u.email,name:u.name,subject:emailSubject,text:emailText||body,template:type});
}


function safeExt(name,mime=''){const ext=path.extname(String(name||'')).toLowerCase();if(/^\.[a-z0-9]{1,8}$/.test(ext))return ext;const m={'video/mp4':'.mp4','video/webm':'.webm','application/pdf':'.pdf','image/jpeg':'.jpg','image/png':'.png','audio/mpeg':'.mp3'};return m[mime]||'.bin'}
function readBinary(req,filePath,limitBytes=536870912){return new Promise((resolve,reject)=>{let total=0;const out=fs.createWriteStream(filePath,{flags:'wx'});req.on('data',c=>{total+=c.length;if(total>limitBytes){reject(Object.assign(new Error('File is too large.'),{status:413}));req.destroy();out.destroy()} });req.pipe(out);out.on('finish',()=>resolve(total));out.on('error',reject);req.on('error',reject)})}
async function ownsCourse(user,courseId){if(user.role==='admin')return true;const r=await db.query('SELECT 1 FROM courses WHERE id=$1 AND instructor_id=$2',[courseId,user.id]);return !!r.rowCount}
async function canReadCourseMedia(user,courseId){if(!user)return false;if(user.role==='admin')return true;if(user.role==='instructor')return ownsCourse(user,courseId);if(user.role==='student'){const r=await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2',[user.id,courseId]);return !!r.rowCount}return false}
function streamMedia(file,res,mime,size,req){const range=req.headers.range;if(range&&size){const m=/bytes=(\d*)-(\d*)/.exec(range);if(m){const start=m[1]?Number(m[1]):0,end=m[2]?Math.min(Number(m[2]),size-1):size-1;if(start<=end){res.writeHead(206,{...securityHeaders,'Content-Type':mime,'Accept-Ranges':'bytes','Content-Range':`bytes ${start}-${end}/${size}`,'Content-Length':end-start+1,'Cache-Control':'private, max-age=300'});return fs.createReadStream(file,{start,end}).pipe(res)}}}res.writeHead(200,{...securityHeaders,'Content-Type':mime,'Content-Length':size,'Accept-Ranges':'bytes','Cache-Control':'private, max-age=300'});fs.createReadStream(file).pipe(res)}


const INSTRUCTOR_SHARE_PERCENT=Math.max(0,Math.min(100,Number(process.env.INSTRUCTOR_SHARE_PERCENT||75)));
const PAYMENT_SANDBOX=/^(1|true|yes)$/i.test(process.env.PAYMENT_SANDBOX||'');
function cents(v){return Math.max(0,Math.round(Number(v||0)))}
async function couponFor(code,userId,subtotal){
  code=String(code||'').trim().toUpperCase(); if(!code)return {code:null,discount:0,coupon:null};
  const r=await db.query(`SELECT c.*,(SELECT count(*) FROM coupon_redemptions cr WHERE cr.coupon_id=c.id) used FROM coupons c WHERE upper(c.code)=upper($1) AND c.active=true AND (c.starts_at IS NULL OR c.starts_at<=now()) AND (c.ends_at IS NULL OR c.ends_at>=now())`,[code]);
  const c=r.rows[0]; if(!c)return {error:'Coupon is invalid or expired.'};
  if(c.max_redemptions!==null && Number(c.used)>=Number(c.max_redemptions))return {error:'Coupon redemption limit has been reached.'};
  let discount=c.discount_type==='percent'?Math.floor(subtotal*Math.min(100,Number(c.discount_value))/100):Math.min(subtotal,Number(c.discount_value));
  return {code:c.code,discount:Math.max(0,discount),coupon:c};
}
async function markOrderPaid(orderId,reference=''){
  const o=await db.query('SELECT * FROM orders WHERE id=$1',[orderId]); if(!o.rowCount)throw Object.assign(new Error('Order not found.'),{status:404});
  if(o.rows[0].status==='paid')return o.rows[0];
  await db.query("UPDATE orders SET status='paid',paid_at=now(),updated_at=now(),payment_reference=COALESCE(NULLIF($2,''),payment_reference) WHERE id=$1",[orderId,reference]);
  await db.query("UPDATE payment_transactions SET status='paid',provider_reference=COALESCE(NULLIF($2,''),provider_reference),updated_at=now() WHERE order_id=$1 AND status<>'paid'",[orderId,reference]);
  const items=await db.query(`SELECT oi.id order_item_id,oi.course_id,oi.unit_price_cents,c.instructor_id FROM order_items oi JOIN courses c ON c.id=oi.course_id WHERE oi.order_id=$1`,[orderId]);
  const share=INSTRUCTOR_SHARE_PERCENT;
  for(const i of items.rows){
    await db.query(`INSERT INTO enrolments(id,student_id,course_id) VALUES($1,$2,$3) ON CONFLICT(student_id,course_id) DO NOTHING`,[uuid(),o.rows[0].student_id,i.course_id]);
    if(i.instructor_id){const instructor=Math.floor(Number(i.unit_price_cents)*share/100),platform=Number(i.unit_price_cents)-instructor;await db.query(`INSERT INTO instructor_earnings(id,instructor_id,order_id,order_item_id,course_id,gross_cents,instructor_cents,platform_cents,status) VALUES($1,$2,$3,$4,$5,$6,$7,$8,'available') ON CONFLICT(order_item_id) DO NOTHING`,[uuid(),i.instructor_id,orderId,i.order_item_id,i.course_id,i.unit_price_cents,instructor,platform])}
  }
  return (await db.query('SELECT * FROM orders WHERE id=$1',[orderId])).rows[0];
}

async function api(req,res,url){
  if(url.pathname==='/api/support-config'&&req.method==='GET')return json(res,200,{email:SUPPORT_EMAIL});
  if(url.pathname==='/api/notifications'&&req.method==='GET'){
    const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{const limit=Math.max(1,Math.min(100,Number(url.searchParams.get('limit')||30)));const r=await db.query('SELECT id,type,title,body,link,read_at,created_at FROM notifications WHERE user_id=$1 ORDER BY created_at DESC LIMIT $2',[u.id,limit]);const unread=await db.query('SELECT count(*)::int n FROM notifications WHERE user_id=$1 AND read_at IS NULL',[u.id]);return json(res,200,{notifications:r.rows,unread:Number(unread.rows[0].n)})}catch(e){return dbFail(res,e)}
  }
  const notificationRead=url.pathname.match(/^\/api\/notifications\/([0-9a-f-]{36})\/read$/i);
  if(notificationRead&&req.method==='POST'){const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{await db.query('UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=$1 AND user_id=$2',[notificationRead[1],u.id]);return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/notifications/read-all'&&req.method==='POST'){const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{await db.query('UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE user_id=$1',[u.id]);return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/notification-preferences'&&req.method==='GET'){const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{return json(res,200,{preferences:await notificationPrefs(u.id),emailConfigured:!!BREVO_API_KEY})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/notification-preferences'&&req.method==='PUT'){const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{const x=await readJson(req),v=k=>x[k]!==false;await db.query(`INSERT INTO notification_preferences(user_id,in_app_messages,email_messages,in_app_announcements,email_announcements,in_app_live_classes,email_live_classes,updated_at) VALUES($1,$2,$3,$4,$5,$6,$7,now()) ON CONFLICT(user_id) DO UPDATE SET in_app_messages=EXCLUDED.in_app_messages,email_messages=EXCLUDED.email_messages,in_app_announcements=EXCLUDED.in_app_announcements,email_announcements=EXCLUDED.email_announcements,in_app_live_classes=EXCLUDED.in_app_live_classes,email_live_classes=EXCLUDED.email_live_classes,updated_at=now()`,[u.id,v('in_app_messages'),v('email_messages'),v('in_app_announcements'),v('email_announcements'),v('in_app_live_classes'),v('email_live_classes')]);return json(res,200,{preferences:await notificationPrefs(u.id)})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/conversations'&&req.method==='POST'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{const x=await readJson(req),courseId=String(x.courseId||'');if(!/^[0-9a-f-]{36}$/i.test(courseId))return json(res,400,{message:'Select a valid course.'});const c=await db.query(`SELECT c.id,c.title,c.instructor_id FROM courses c JOIN enrolments e ON e.course_id=c.id WHERE c.id=$1 AND e.student_id=$2`,[courseId,u.id]);if(!c.rowCount)return json(res,403,{message:'You can message instructors for courses you are enrolled in.'});let r=await db.query('SELECT id FROM conversations WHERE student_id=$1 AND instructor_id=$2 AND course_id=$3',[u.id,c.rows[0].instructor_id,courseId]);if(!r.rowCount){const id=uuid();await db.query('INSERT INTO conversations(id,student_id,instructor_id,course_id) VALUES($1,$2,$3,$4)',[id,u.id,c.rows[0].instructor_id,courseId]);r={rows:[{id}]}}return json(res,201,{conversationId:r.rows[0].id})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/announcements'&&req.method==='GET'){
    const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{let r;if(u.role==='student')r=await db.query(`SELECT a.id,a.title,a.body,a.created_at,c.title course_title,au.name author_name FROM course_announcements a JOIN courses c ON c.id=a.course_id JOIN users au ON au.id=a.author_id JOIN enrolments e ON e.course_id=a.course_id WHERE e.student_id=$1 ORDER BY a.created_at DESC LIMIT 100`,[u.id]);else if(u.role==='instructor')r=await db.query(`SELECT a.id,a.title,a.body,a.created_at,c.title course_title FROM course_announcements a JOIN courses c ON c.id=a.course_id WHERE a.author_id=$1 ORDER BY a.created_at DESC LIMIT 100`,[u.id]);else r=await db.query(`SELECT a.id,a.title,a.body,a.created_at,c.title course_title,au.name author_name FROM course_announcements a JOIN courses c ON c.id=a.course_id JOIN users au ON au.id=a.author_id ORDER BY a.created_at DESC LIMIT 100`);return json(res,200,{announcements:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/announcements'&&req.method==='POST'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const x=await readJson(req),courseId=String(x.courseId||''),title=String(x.title||'').trim(),body=String(x.body||'').trim(),emailStudents=x.emailStudents!==false;if(!/^[0-9a-f-]{36}$/i.test(courseId)||title.length<3||body.length<3)return json(res,400,{message:'Choose a course and enter an announcement title and message.'});if(u.role==='instructor'&&!(await ownsCourse(u,courseId)))return json(res,403,{message:'You can only announce to students in your own courses.'});const id=uuid();await db.query('INSERT INTO course_announcements(id,course_id,author_id,title,body,email_students) VALUES($1,$2,$3,$4,$5,$6)',[id,courseId,u.id,title,body,emailStudents]);const course=(await db.query('SELECT title FROM courses WHERE id=$1',[courseId])).rows[0];const learners=await db.query('SELECT u.id FROM enrolments e JOIN users u ON u.id=e.student_id WHERE e.course_id=$1',[courseId]);for(const row of learners.rows)await notifyUser(row.id,{type:'course_announcement',title,body:`${course?.title||'Course'}: ${body}`,link:'/my-learning.html',emailSubject:emailStudents?`EduQuinn course announcement: ${title}`:'',emailText:`${course?.title||'Your course'}\n\n${title}\n\n${body}`,channel:'announcements'});await audit(u,'announcement.created','course',courseId,{announcementId:id,title,recipients:learners.rowCount});return json(res,201,{id,recipients:learners.rowCount})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/auth/session'&&req.method==='GET'){
    try{return json(res,200,{user:publicUser(await currentUser(req))})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/auth/register'&&req.method==='POST'){
    try{const x=await readJson(req),name=String(x.name||'').trim(),email=String(x.email||'').trim().toLowerCase(),role=x.role,expertise=String(x.expertise||'').trim(),password=String(x.password||'');
      const launch=(await db.query('SELECT * FROM platform_launch_state WHERE id=1')).rows[0];
      if(launch&&!launch.registrations_open)return json(res,403,{message:'New registrations are temporarily closed.'});
      if(role==='instructor'&&launch&&!launch.instructor_applications_open)return json(res,403,{message:'Instructor applications are temporarily closed.'});
      if(name.length<2||!validEmail(email)||password.length<8||!['student','instructor'].includes(role))return json(res,400,{message:'Please provide valid registration details.'});
      if(role==='instructor'&&!expertise)return json(res,400,{message:'Please select your primary expertise.'});
      const exists=await db.query('SELECT 1 FROM users WHERE lower(email)=lower($1)',[email]);if(exists.rowCount)return json(res,409,{message:'An account with this email address already exists.'});
      const id=uuid(),hash=await bcrypt.hash(password,12);await db.query('INSERT INTO users(id,name,email,password_hash,role,status,expertise) VALUES($1,$2,$3,$4,$5,$6,$7)',[id,name,email,hash,role,'active',role==='instructor'?expertise:null]);
      const token=crypto.randomBytes(32).toString('hex'),sid=uuid(),seconds=SESSION_DAYS*86400;await db.query(`INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES($1,$2,$3,now()+($4||' seconds')::interval)`,[sid,id,sha(token),seconds]);setSessionCookie(res,token,seconds);await audit({id},'account.registered','user',id,{role});
      return json(res,201,{user:{id,name,email,role,status:'active',expertise:role==='instructor'?expertise:''}})
    }catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/auth/login'&&req.method==='POST'){
    try{const x=await readJson(req),email=String(x.email||'').trim().toLowerCase(),password=String(x.password||'');const r=await db.query('SELECT * FROM users WHERE lower(email)=lower($1)',[email]);const u=r.rows[0];if(!u||!(await bcrypt.compare(password,u.password_hash))){await securityEvent(req,'auth.login_failed',u?.id||null,{email});return json(res,401,{message:'The email address or password is incorrect.'})}if(u.status!=='active'){await securityEvent(req,'auth.login_blocked',u.id,{status:u.status});return json(res,403,{message:'This account is not active.'})};
      if(x.portal==='public'&&u.role==='admin')return json(res,403,{message:'Administrator sign-in is not available from the public login page.'});if(x.portal==='admin'&&u.role!=='admin')return json(res,403,{message:'This account is not authorised for administration.'});if(x.portal==='public'&&x.expectedRole&&u.role!==x.expectedRole)return json(res,403,{message:x.expectedRole==='student'?'This is not a student account. Choose Teacher login if you are an instructor.':'This is not a teacher account. Choose Student login if you are a learner.'});
      const token=crypto.randomBytes(32).toString('hex'),sid=uuid(),seconds=SESSION_DAYS*86400;await db.query(`INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES($1,$2,$3,now()+($4||' seconds')::interval)`,[sid,u.id,sha(token),seconds]);setSessionCookie(res,token,seconds);await audit(u,'auth.login','user',u.id,{portal:x.portal||'public'});await securityEvent(req,'auth.login_succeeded',u.id,{portal:x.portal||'public'});return json(res,200,{user:publicUser(u)})
    }catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/auth/logout'&&req.method==='POST'){
    try{const token=cookieMap(req).eq_session;if(token)await db.query('DELETE FROM sessions WHERE token_hash=$1',[sha(token)]);clearSessionCookie(res);return json(res,200,{ok:true})}catch(e){clearSessionCookie(res);return json(res,200,{ok:true})}
  }
  if(url.pathname==='/api/courses'&&req.method==='GET'){
    try{const level=url.searchParams.get('level'),params=[],where=["c.status='published'"];if(level){params.push(level);where.push(`c.education_level=$${params.length}`)}const r=await db.query(`SELECT c.id,c.title,c.slug,c.subtitle,c.description,c.education_level,c.category,c.price_cents,c.currency,c.quality_score,u.name instructor_name,(SELECT count(*)::int FROM enrolments e WHERE e.course_id=c.id) students,(SELECT round(avg(r.rating)::numeric,1) FROM reviews r WHERE r.course_id=c.id AND r.status='published') rating,(SELECT count(*)::int FROM reviews r WHERE r.course_id=c.id AND r.status='published') review_count FROM courses c LEFT JOIN users u ON u.id=c.instructor_id WHERE ${where.join(' AND ')} ORDER BY c.created_at DESC LIMIT 100`,params);return json(res,200,{courses:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/public/stats'&&req.method==='GET'){
    try{const r=await db.query(`SELECT (SELECT count(*)::int FROM users WHERE role='student' AND status='active') students,(SELECT count(*)::int FROM users WHERE role='instructor' AND status='active') instructors,(SELECT count(*)::int FROM courses WHERE status='published') courses`);return json(res,200,r.rows[0])}catch(e){return dbFail(res,e)}
  }
  const publicCourse=url.pathname.match(/^\/api\/courses\/([0-9a-f-]{36})$/i);
  if(publicCourse&&req.method==='GET'){
    try{const r=await db.query(`SELECT c.*,u.name instructor_name,(SELECT count(*)::int FROM enrolments e WHERE e.course_id=c.id) students,(SELECT round(avg(rv.rating)::numeric,1) FROM reviews rv WHERE rv.course_id=c.id AND rv.status='published') rating,(SELECT count(*)::int FROM lessons l WHERE l.course_id=c.id) lesson_count FROM courses c LEFT JOIN users u ON u.id=c.instructor_id WHERE c.id=$1 AND c.status='published'`,[publicCourse[1]]);if(!r.rowCount)return json(res,404,{message:'Course not found.'});const rv=await db.query(`SELECT r.rating,r.review,r.created_at,u.name student_name FROM reviews r JOIN users u ON u.id=r.student_id WHERE r.course_id=$1 AND r.status='published' ORDER BY r.created_at DESC LIMIT 50`,[publicCourse[1]]);return json(res,200,{course:r.rows[0],reviews:rv.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/wishlist'&&req.method==='GET'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{const r=await db.query(`SELECT c.id,c.title,c.price_cents,c.currency,c.education_level,c.category,i.name instructor_name FROM wishlist w JOIN courses c ON c.id=w.course_id LEFT JOIN users i ON i.id=c.instructor_id WHERE w.user_id=$1 AND c.status='published' ORDER BY w.created_at DESC`,[u.id]);return json(res,200,{courses:r.rows})}catch(e){return dbFail(res,e)}
  }
  const wish=url.pathname.match(/^\/api\/wishlist\/([0-9a-f-]{36})$/i);
  if(wish&&req.method==='POST'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{const c=await db.query("SELECT 1 FROM courses WHERE id=$1 AND status='published'",[wish[1]]);if(!c.rowCount)return json(res,404,{message:'Course not found.'});await db.query('INSERT INTO wishlist(user_id,course_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[u.id,wish[1]]);return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}
  }
  if(wish&&req.method==='DELETE'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{await db.query('DELETE FROM wishlist WHERE user_id=$1 AND course_id=$2',[u.id,wish[1]]);return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/instructor/courses'&&req.method==='GET'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const r=await db.query(`SELECT * FROM courses WHERE instructor_id=$1 ORDER BY updated_at DESC`,[u.id]);return json(res,200,{courses:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/instructor/courses'&&req.method==='POST'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const x=await readJson(req),title=String(x.title||'').trim();if(title.length<4)return json(res,400,{message:'Course title is required.'});const id=uuid(),slug=slugify(title)+'-'+id.slice(0,8);await db.query(`INSERT INTO courses(id,instructor_id,title,slug,subtitle,description,education_level,category,price_cents,currency,status) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,'USD','draft')`,[id,u.id,title,slug,String(x.subtitle||''),String(x.description||''),String(x.educationLevel||'professional'),String(x.category||''),Math.max(0,Math.round(Number(x.price||0)*100))]);await audit(u,'course.created','course',id,{title});return json(res,201,{id,slug})}catch(e){return dbFail(res,e)}
  }
  const instructorCourseMatch =
  url.pathname.match(
    /^\/api\/instructor\/courses\/([0-9a-f-]{36})$/i
  );


/* Edit course */
if (instructorCourseMatch && req.method === 'PATCH') {

  const u = await requireRole(
    req,
    res,
    ['instructor', 'admin']
  );

  if (!u) return;

  try {

    const courseId = instructorCourseMatch[1];

    if (!(await ownsCourse(u, courseId))) {
      return json(res, 403, {
        message: 'You do not manage this course.'
      });
    }

    const current = await db.query(
      'SELECT * FROM courses WHERE id=$1',
      [courseId]
    );

    if (!current.rowCount) {
      return json(res, 404, {
        message: 'Course not found.'
      });
    }

    const x = await readJson(req);

    const title =
      String(
        x.title ?? current.rows[0].title
      ).trim();

    const category =
      String(
        x.category ?? current.rows[0].category ?? ''
      ).trim();

    const educationLevel =
      String(
        x.educationLevel ??
        current.rows[0].education_level
      ).trim();

    let priceCents =
      Number(current.rows[0].price_cents || 0);

    if (x.price !== undefined) {
      priceCents =
        Math.max(
          0,
          Math.round(Number(x.price || 0) * 100)
        );
    }

    if (title.length < 4) {
      return json(res, 400, {
        message:
          'Course title must contain at least 4 characters.'
      });
    }

    await db.query(
      `UPDATE courses
       SET title=$2,
           category=$3,
           education_level=$4,
           price_cents=$5,
           updated_at=now()
       WHERE id=$1`,
      [
        courseId,
        title,
        category,
        educationLevel,
        priceCents
      ]
    );

    await audit(
      u,
      'course.updated',
      'course',
      courseId,
      {
        title,
        category,
        educationLevel,
        priceCents
      }
    );

    return json(res, 200, {
      ok: true,
      id: courseId
    });

  } catch (e) {
    return dbFail(res, e);
  }
}


/* Delete / safely archive course */
if (instructorCourseMatch && req.method === 'DELETE') {

  const u = await requireRole(
    req,
    res,
    ['instructor', 'admin']
  );

  if (!u) return;

  try {

    const courseId = instructorCourseMatch[1];

    if (!(await ownsCourse(u, courseId))) {
      return json(res, 403, {
        message: 'You do not manage this course.'
      });
    }

    const course = await db.query(
      'SELECT * FROM courses WHERE id=$1',
      [courseId]
    );

    if (!course.rowCount) {
      return json(res, 404, {
        message: 'Course not found.'
      });
    }

    const history = await db.query(
      `SELECT
        (SELECT count(*)
         FROM enrolments
         WHERE course_id=$1)::int AS enrolments,

        (SELECT count(*)
         FROM order_items
         WHERE course_id=$1)::int AS orders`,
      [courseId]
    );

    const hasHistory =
      Number(history.rows[0].enrolments) > 0 ||
      Number(history.rows[0].orders) > 0;

    if (hasHistory) {

      await db.query(
        `UPDATE courses
         SET status='archived',
             updated_at=now()
         WHERE id=$1`,
        [courseId]
      );

      await audit(
        u,
        'course.archived',
        'course',
        courseId,
        {}
      );

      return json(res, 200, {
        ok: true,
        archived: true,
        message:
          'This course has student or transaction history, so it was archived instead of permanently deleted.'
      });
    }

    await db.query(
      'DELETE FROM courses WHERE id=$1',
      [courseId]
    );

    await audit(
      u,
      'course.deleted',
      'course',
      courseId,
      {}
    );

    return json(res, 200, {
      ok: true,
      deleted: true,
      message:
        'Course permanently deleted.'
    });

  } catch (e) {
    return dbFail(res, e);
  }
}
  if(url.pathname==='/api/enrolments'&&req.method==='GET'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{const r=await db.query(`SELECT e.*,c.title,c.slug,c.education_level,c.category FROM enrolments e JOIN courses c ON c.id=e.course_id WHERE e.student_id=$1 ORDER BY e.enrolled_at DESC`,[u.id]);return json(res,200,{enrolments:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/conversations'&&req.method==='GET'){
    const u=await requireRole(req,res,['student','instructor']);if(!u)return;try{const col=u.role==='student'?'student_id':'instructor_id';const r=await db.query(`SELECT c.id,c.course_id,c.updated_at,co.title course_title,s.id student_id,s.name student_name,s.email student_email,i.id instructor_id,i.name instructor_name,i.email instructor_email,(SELECT body FROM messages m WHERE m.conversation_id=c.id ORDER BY created_at DESC LIMIT 1) last_message FROM conversations c JOIN users s ON s.id=c.student_id JOIN users i ON i.id=c.instructor_id LEFT JOIN courses co ON co.id=c.course_id WHERE c.${col}=$1 ORDER BY c.updated_at DESC`,[u.id]);return json(res,200,{conversations:r.rows})}catch(e){return dbFail(res,e)}
  }
  const msgMatch=url.pathname.match(/^\/api\/conversations\/([0-9a-f-]{36})\/messages$/i);
  if(msgMatch&&req.method==='GET'){
    const u=await requireRole(req,res,['student','instructor']);if(!u)return;try{const c=await db.query('SELECT * FROM conversations WHERE id=$1 AND (student_id=$2 OR instructor_id=$2)',[msgMatch[1],u.id]);if(!c.rowCount)return json(res,404,{message:'Conversation not found.'});const r=await db.query(`SELECT m.id,m.body,m.created_at,m.read_at,m.sender_id,u.name sender_name,u.role sender_role FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=$1 ORDER BY m.created_at`,[msgMatch[1]]);await db.query('UPDATE messages SET read_at=COALESCE(read_at,now()) WHERE conversation_id=$1 AND sender_id<>$2',[msgMatch[1],u.id]);return json(res,200,{messages:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(msgMatch&&req.method==='POST'){
    const u=await requireRole(req,res,['student','instructor']);if(!u)return;try{const x=await readJson(req),body=String(x.body||'').trim();if(!body||body.length>5000)return json(res,400,{message:'Message must contain between 1 and 5000 characters.'});const c=await db.query('SELECT * FROM conversations WHERE id=$1 AND (student_id=$2 OR instructor_id=$2)',[msgMatch[1],u.id]);if(!c.rowCount)return json(res,404,{message:'Conversation not found.'});const id=uuid();await db.query('INSERT INTO messages(id,conversation_id,sender_id,body) VALUES($1,$2,$3,$4)',[id,msgMatch[1],u.id,body]);await db.query('UPDATE conversations SET updated_at=now() WHERE id=$1',[msgMatch[1]]);const cv=c.rows[0],recipient=u.role==='student'?cv.instructor_id:cv.student_id;await notifyUser(recipient,{type:'message',title:`New message from ${u.name}`,body:body.slice(0,240),link:u.role==='student'?'/instructor-messages.html':'/messages.html',emailSubject:`New EduQuinn message from ${u.name}`,emailText:`${u.name} sent you a message on EduQuinn:

${body}

Sign in to reply.`,channel:'messages'});return json(res,201,{id})}catch(e){return dbFail(res,e)}
  }

  const assetsMatch=url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/assets$/i);
  if(assetsMatch&&req.method==='GET'){const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{if(!(await ownsCourse(u,assetsMatch[1])))return json(res,403,{message:'You do not manage this course.'});const r=await db.query('SELECT id,kind,original_name,mime_type,size_bytes,created_at FROM course_assets WHERE course_id=$1 ORDER BY created_at DESC',[assetsMatch[1]]);return json(res,200,{assets:r.rows})}catch(e){return dbFail(res,e)}}
  if(assetsMatch&&req.method==='PUT'){const u=await requireRole(req,res,['instructor','admin']);if(!u)return;let tmp='';try{const courseId=assetsMatch[1];if(!(await ownsCourse(u,courseId)))return json(res,403,{message:'You do not manage this course.'});const name=decodeURIComponent(url.searchParams.get('name')||'upload.bin').slice(0,255),kind=String(url.searchParams.get('kind')||'other').toLowerCase(),allowed=['video','document','audio','thumbnail','other'];if(!allowed.includes(kind))return json(res,400,{message:'Unsupported media type.'});const mime=String(req.headers['content-type']||'application/octet-stream').split(';')[0].slice(0,160),id=uuid(),stored=id+safeExt(name,mime);tmp=path.join(MEDIA_ROOT,stored+'.part');const size=await readBinary(req,tmp,536870912);const finalPath=path.join(MEDIA_ROOT,stored);fs.renameSync(tmp,finalPath);await db.query('INSERT INTO course_assets(id,course_id,uploaded_by,kind,original_name,stored_name,mime_type,size_bytes) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',[id,courseId,u.id,kind,name,stored,mime,size]);await audit(u,'media.uploaded','course',courseId,{assetId:id,name,kind,size});return json(res,201,{asset:{id,courseId,kind,originalName:name,mimeType:mime,sizeBytes:size,url:`/api/media/${id}`}})}catch(e){if(tmp)fs.rm(tmp,{force:true},()=>{});return e.status?json(res,e.status,{message:e.message}):dbFail(res,e)}}
  const mediaMatch=url.pathname.match(/^\/api\/media\/([0-9a-f-]{36})$/i);
  if(mediaMatch&&req.method==='GET'){try{const r=await db.query('SELECT * FROM course_assets WHERE id=$1',[mediaMatch[1]]),a=r.rows[0];if(!a)return json(res,404,{message:'Media not found.'});let user=null;try{user=await currentUser(req)}catch{}let preview=false;if(a.kind==='thumbnail')preview=true;else{const pr=await db.query('SELECT 1 FROM lessons WHERE asset_id=$1 AND is_preview=true LIMIT 1',[a.id]);preview=!!pr.rowCount}if(!preview&&!(await canReadCourseMedia(user,a.course_id)))return json(res,user?403:401,{message:'Sign in with course access to view this media.'});const file=path.join(MEDIA_ROOT,a.stored_name);if(!fs.existsSync(file))return json(res,404,{message:'Media file is missing.'});return streamMedia(file,res,a.mime_type,Number(a.size_bytes),req)}catch(e){return dbFail(res,e)}}
  const lessonsMatch=url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/lessons$/i);
  if(lessonsMatch&&req.method==='GET'){const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{if(!(await ownsCourse(u,lessonsMatch[1])))return json(res,403,{message:'You do not manage this course.'});const r=await db.query('SELECT l.*,a.original_name asset_name,a.kind asset_kind FROM lessons l LEFT JOIN course_assets a ON a.id=l.asset_id WHERE l.course_id=$1 ORDER BY l.position,l.created_at',[lessonsMatch[1]]);return json(res,200,{lessons:r.rows})}catch(e){return dbFail(res,e)}}
  if (lessonsMatch && req.method === 'POST') {
  const u = await requireRole(req, res, ['instructor', 'admin']);
  if (!u) return;

  try {
    const courseId = lessonsMatch[1];

    if (!(await ownsCourse(u, courseId))) {
      return json(res, 403, {
        message: 'You do not manage this course.'
      });
    }

    const x = await readJson(req, 250000);

    const title = String(x.title || '').trim();
    const lessonType = [
      'video',
      'article',
      'quiz',
      'assignment',
      'resource'
    ].includes(x.lessonType)
      ? x.lessonType
      : 'video';

    if (title.length < 2) {
      return json(res, 400, {
        message: 'Lesson title is required.'
      });
    }

    const lessonId = uuid();

    let body = String(x.body || '');

    if (lessonType === 'assignment') {
      const assignment = x.assignment || {};

      body = JSON.stringify({
        instructions:
          String(assignment.instructions || '').trim(),

        dueDate:
          assignment.dueDate || null,

        maxMarks:
          Math.max(
            1,
            Number(assignment.maxMarks || 100)
          )
      });

      if (!assignment.instructions) {
        return json(res, 400, {
          message: 'Assignment instructions are required.'
        });
      }
    }

    await db.query('BEGIN');

    await db.query(
      `INSERT INTO lessons(
        id,
        course_id,
        title,
        lesson_type,
        position,
        asset_id,
        body,
        duration_seconds,
        is_preview,
        metadata
      )
      VALUES(
        $1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb
      )`,
      [
        lessonId,
        courseId,
        title,
        lessonType,
        Number(x.position || 0),
        x.assetId || null,
        body,
        Math.max(0, Number(x.durationSeconds || 0)),
        !!x.isPreview,
        JSON.stringify(
          lessonType === 'assignment'
            ? x.assignment || {}
            : {}
        )
      ]
    );

    if (lessonType === 'quiz') {
      const quiz = x.quiz || {};
      const questions =
        Array.isArray(quiz.questions)
          ? quiz.questions
          : [];

      if (!questions.length) {
        await db.query('ROLLBACK');

        return json(res, 400, {
          message: 'Add at least one quiz question.'
        });
      }

      const assessmentId = uuid();

      const passMark = Math.max(
        0,
        Math.min(100, Number(quiz.passMark || 70))
      );

      const maxAttempts = Math.max(
        1,
        Math.min(100, Number(quiz.maxAttempts || 3))
      );

      const timeLimit = Math.max(
        0,
        Math.min(
          1440,
          Number(quiz.timeLimitMinutes || 0)
        )
      );

      await db.query(
        `INSERT INTO assessments(
          id,
          course_id,
          lesson_id,
          title,
          pass_mark,
          instructions,
          max_attempts,
          time_limit_minutes,
          shuffle_questions,
          show_answers
        )
        VALUES(
          $1,$2,$3,$4,$5,$6,$7,$8,$9,$10
        )`,
        [
          assessmentId,
          courseId,
          lessonId,
          title,
          passMark,
          String(quiz.instructions || ''),
          maxAttempts,
          timeLimit,
          !!quiz.shuffleQuestions,
          quiz.showAnswers !== false
        ]
      );

      for (
        let i = 0;
        i < questions.length;
        i++
      ) {
        const q = questions[i] || {};

        const prompt =
          String(q.prompt || '').trim();

        if (!prompt) {
          await db.query('ROLLBACK');

          return json(res, 400, {
            message:
              'Every quiz question requires question text.'
          });
        }

        const type = [
          'single',
          'multiple',
          'true_false',
          'short_answer'
        ].includes(q.type)
          ? q.type
          : 'single';

        const rawOptions =
          Array.isArray(q.options)
            ? q.options
            : [];

        const options = rawOptions
          .map((option, index) => ({
            text:
              String(option.text || '').trim(),

            position:
              Number(option.position || index + 1)
          }))
          .filter(option => option.text);

        const correctAnswers = rawOptions
          .map((option, index) => ({
            index,
            text:
              String(option.text || '').trim(),

            correct:
              !!option.correct
          }))
          .filter(
            option =>
              option.correct && option.text
          )
          .map(option => ({
            index: option.index,
            text: option.text
          }));

        if (
          type !== 'short_answer' &&
          options.length < 2
        ) {
          await db.query('ROLLBACK');

          return json(res, 400, {
            message:
              'Each quiz question needs at least two answers.'
          });
        }

        if (!correctAnswers.length) {
          await db.query('ROLLBACK');

          return json(res, 400, {
            message:
              'Each quiz question requires a correct answer.'
          });
        }

        if (
          ['single', 'true_false'].includes(type) &&
          correctAnswers.length !== 1
        ) {
          await db.query('ROLLBACK');

          return json(res, 400, {
            message:
              'Single-answer questions must have exactly one correct answer.'
          });
        }

        await db.query(
          `INSERT INTO assessment_questions(
            id,
            assessment_id,
            prompt,
            options,
            correct_index,
            position,
            question_type,
            correct_answers,
            marks,
            explanation
          )
          VALUES(
            $1,$2,$3,$4::jsonb,$5,$6,$7,$8::jsonb,$9,$10
          )`,
          [
            uuid(),
            assessmentId,
            prompt,
            JSON.stringify(options),
            correctAnswers.length === 1
              ? correctAnswers[0].index
              : null,
            Number(q.position || i + 1),
            type,
            JSON.stringify(correctAnswers),
            Math.max(1, Number(q.marks || 1)),
            String(q.explanation || '')
          ]
        );
      }
    }

    await db.query('COMMIT');

    await audit(
      u,
      'lesson.created',
      'course',
      courseId,
      {
        lessonId,
        lessonType,
        title
      }
    );

    return json(res, 201, {
      id: lessonId,
      lessonType
    });

  } catch (e) {
    try {
      await db.query('ROLLBACK');
    } catch {}

    return e.status
      ? json(res, e.status, {
          message: e.message
        })
      : dbFail(res, e);
  }
}
  const learningMatch=url.pathname.match(/^\/api\/learning\/courses\/([0-9a-f-]{36})$/i);
  if(learningMatch&&req.method==='GET'){const u=await requireRole(req,res,['student']);if(!u)return;try{const enr=await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2',[u.id,learningMatch[1]]);if(!enr.rowCount)return json(res,403,{message:'You are not enrolled in this course.'});const r=await db.query(`SELECT l.id,l.title,l.lesson_type,l.position,l.duration_seconds,l.is_preview,l.asset_id,COALESCE(p.completed,false) completed,COALESCE(p.position_seconds,0) position_seconds FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=$1 WHERE l.course_id=$2 ORDER BY l.position,l.created_at`,[u.id,learningMatch[1]]);return json(res,200,{lessons:r.rows})}catch(e){return dbFail(res,e)}}
  const progressMatch=url.pathname.match(/^\/api\/learning\/lessons\/([0-9a-f-]{36})\/progress$/i);
  if(progressMatch&&req.method==='POST'){const u=await requireRole(req,res,['student']);if(!u)return;try{const l=await db.query('SELECT course_id FROM lessons WHERE id=$1',[progressMatch[1]]);if(!l.rowCount)return json(res,404,{message:'Lesson not found.'});const enr=await db.query('SELECT id FROM enrolments WHERE student_id=$1 AND course_id=$2',[u.id,l.rows[0].course_id]);if(!enr.rowCount)return json(res,403,{message:'You are not enrolled in this course.'});const x=await readJson(req);await db.query(`INSERT INTO lesson_progress(student_id,lesson_id,completed,position_seconds,completed_at) VALUES($1,$2,$3,$4,CASE WHEN $3 THEN now() ELSE NULL END) ON CONFLICT(student_id,lesson_id) DO UPDATE SET completed=EXCLUDED.completed,position_seconds=EXCLUDED.position_seconds,updated_at=now(),completed_at=CASE WHEN EXCLUDED.completed THEN COALESCE(lesson_progress.completed_at,now()) ELSE NULL END`,[u.id,progressMatch[1],!!x.completed,Math.max(0,Number(x.positionSeconds||0))]);const pct=await db.query(`SELECT CASE WHEN count(*)=0 THEN 0 ELSE round(100.0*count(*) FILTER (WHERE p.completed)/count(*)) END pct FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=$1 WHERE l.course_id=$2`,[u.id,l.rows[0].course_id]);await db.query('UPDATE enrolments SET progress=$1,completed_at=CASE WHEN $1=100 THEN COALESCE(completed_at,now()) ELSE NULL END WHERE student_id=$2 AND course_id=$3',[Number(pct.rows[0].pct||0),u.id,l.rows[0].course_id]);return json(res,200,{progress:Number(pct.rows[0].pct||0)})}catch(e){return dbFail(res,e)}}
  const assessmentCourseMatch=url.pathname.match(/^\/api\/instructor\/courses\/([0-9a-f-]{36})\/assessments$/i);
  if(assessmentCourseMatch&&req.method==='POST'){const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const courseId=assessmentCourseMatch[1];if(!(await ownsCourse(u,courseId)))return json(res,403,{message:'You do not manage this course.'});const x=await readJson(req,150000),title=String(x.title||'').trim(),questions=Array.isArray(x.questions)?x.questions:[];if(title.length<2||!questions.length)return json(res,400,{message:'Assessment title and at least one question are required.'});const id=uuid();await db.query('BEGIN');await db.query('INSERT INTO assessments(id,course_id,title,pass_mark) VALUES($1,$2,$3,$4)',[id,courseId,title,Math.max(0,Math.min(100,Number(x.passMark||50)))]);for(let i=0;i<questions.length;i++){const q=questions[i]||{},options=Array.isArray(q.options)?q.options.slice(0,10):[];await db.query('INSERT INTO assessment_questions(id,assessment_id,prompt,options,correct_index,position) VALUES($1,$2,$3,$4::jsonb,$5,$6)',[uuid(),id,String(q.prompt||'Question '+(i+1)),JSON.stringify(options),Number.isInteger(q.correctIndex)?q.correctIndex:null,i])}await db.query('COMMIT');return json(res,201,{id})}catch(e){try{await db.query('ROLLBACK')}catch{}return dbFail(res,e)}}
  const learningAssessMatch=url.pathname.match(/^\/api\/learning\/courses\/([0-9a-f-]{36})\/assessments$/i);
  if(learningAssessMatch&&req.method==='GET'){const u=await requireRole(req,res,['student']);if(!u)return;try{const e=await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2',[u.id,learningAssessMatch[1]]);if(!e.rowCount)return json(res,403,{message:'You are not enrolled in this course.'});const a=await db.query('SELECT id,title,pass_mark FROM assessments WHERE course_id=$1 ORDER BY created_at',[learningAssessMatch[1]]);for(const item of a.rows){const q=await db.query('SELECT id,prompt,options,position FROM assessment_questions WHERE assessment_id=$1 ORDER BY position',[item.id]);item.questions=q.rows}return json(res,200,{assessments:a.rows})}catch(e){return dbFail(res,e)}}
  const submitAssessMatch=url.pathname.match(/^\/api\/learning\/assessments\/([0-9a-f-]{36})\/submit$/i);
  if(submitAssessMatch&&req.method==='POST'){const u=await requireRole(req,res,['student']);if(!u)return;try{const a=await db.query('SELECT * FROM assessments WHERE id=$1',[submitAssessMatch[1]]);if(!a.rowCount)return json(res,404,{message:'Assessment not found.'});const e=await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2',[u.id,a.rows[0].course_id]);if(!e.rowCount)return json(res,403,{message:'You are not enrolled in this course.'});const x=await readJson(req),answers=Array.isArray(x.answers)?x.answers:[],q=await db.query('SELECT correct_index,position FROM assessment_questions WHERE assessment_id=$1 ORDER BY position',[submitAssessMatch[1]]);let correct=0,total=0;q.rows.forEach((v,i)=>{if(v.correct_index!==null){total++;if(Number(answers[i])===Number(v.correct_index))correct++}});const score=total?Math.round(correct*100/total):0,passed=score>=Number(a.rows[0].pass_mark);const id=uuid();await db.query('INSERT INTO assessment_attempts(id,assessment_id,student_id,answers,score,passed) VALUES($1,$2,$3,$4::jsonb,$5,$6)',[id,submitAssessMatch[1],u.id,JSON.stringify(answers),score,passed]);return json(res,201,{attemptId:id,score,passed,passMark:Number(a.rows[0].pass_mark)})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/certificates/issue'&&req.method==='POST'){const u=await requireRole(req,res,['student']);if(!u)return;try{const x=await readJson(req),courseId=String(x.courseId||'');const e=await db.query('SELECT progress FROM enrolments WHERE student_id=$1 AND course_id=$2',[u.id,courseId]);if(!e.rowCount||Number(e.rows[0].progress)<100)return json(res,409,{message:'Complete all lessons before requesting a certificate.'});const existing=await db.query('SELECT * FROM certificates WHERE student_id=$1 AND course_id=$2',[u.id,courseId]);if(existing.rowCount)return json(res,200,{certificate:existing.rows[0]});const id=uuid(),code='EQ-CERT-'+new Date().getFullYear()+'-'+crypto.randomBytes(5).toString('hex').toUpperCase();const r=await db.query('INSERT INTO certificates(id,certificate_code,student_id,course_id) VALUES($1,$2,$3,$4) RETURNING *',[id,code,u.id,courseId]);return json(res,201,{certificate:r.rows[0]})}catch(e){return dbFail(res,e)}}


  if(url.pathname==='/api/commerce/config'&&req.method==='GET')return json(res,200,{currency:'USD',instructorSharePercent:INSTRUCTOR_SHARE_PERCENT,paymentSandbox:PAYMENT_SANDBOX,methods:[{id:'card',label:'Visa / Mastercard',configured:!!process.env.CARD_PROVIDER&&process.env.CARD_PROVIDER!=='not_configured'},{id:'paynow',label:'EcoCash / Paynow',configured:!!process.env.PAYNOW_INTEGRATION_ID&&!!process.env.PAYNOW_INTEGRATION_KEY},{id:'bank_transfer',label:'Bank transfer',configured:true}]});
  if(url.pathname==='/api/coupons/validate'&&req.method==='POST'){const u=await requireRole(req,res,['student']);if(!u)return;try{const x=await readJson(req),result=await couponFor(x.code,u.id,cents(x.subtotalCents));if(result.error)return json(res,400,{message:result.error});return json(res,200,{code:result.code,discountCents:result.discount})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/orders'&&req.method==='POST'){const u=await requireRole(req,res,['student']);if(!u)return;try{const x=await readJson(req),ids=Array.isArray(x.courseIds)?[...new Set(x.courseIds.map(String).filter(v=>/^[0-9a-f-]{36}$/i.test(v)))]:[],method=String(x.paymentMethod||'');if(!ids.length)return json(res,400,{message:'Select at least one database-backed course.'});if(!['card','paynow','bank_transfer'].includes(method))return json(res,400,{message:'Select a supported payment method.'});const q=await db.query(`SELECT id,title,price_cents,currency FROM courses WHERE id=ANY($1::uuid[]) AND status='published'`,[ids]);if(q.rowCount!==ids.length)return json(res,400,{message:'One or more courses are unavailable.'});const subtotal=q.rows.reduce((a,c)=>a+Number(c.price_cents),0),cp=await couponFor(x.couponCode,u.id,subtotal);if(cp.error)return json(res,400,{message:cp.error});const discount=cp.discount||0,total=Math.max(0,subtotal-discount),id=uuid(),status=total===0?'paid':method==='bank_transfer'?'awaiting_payment':'pending_gateway';await db.query(`INSERT INTO orders(id,student_id,status,payment_method,currency,total_cents,subtotal_cents,discount_cents,coupon_code) VALUES($1,$2,$3,$4,'USD',$5,$6,$7,$8)`,[id,u.id,status,method,total,subtotal,discount,cp.code]);for(const c of q.rows)await db.query('INSERT INTO order_items(id,order_id,course_id,unit_price_cents) VALUES($1,$2,$3,$4)',[uuid(),id,c.id,c.price_cents]);if(cp.coupon)await db.query('INSERT INTO coupon_redemptions(coupon_id,user_id,order_id) VALUES($1,$2,$3)',[cp.coupon.id,u.id,id]);const txid=uuid(),provider=method==='bank_transfer'?'manual_bank':method;await db.query('INSERT INTO payment_transactions(id,order_id,provider,amount_cents,currency,status) VALUES($1,$2,$3,$4,\'USD\',$5)',[txid,id,provider,total,status==='paid'?'paid':'pending']);let order;if(total===0)order=await markOrderPaid(id,'FREE');else if(PAYMENT_SANDBOX&&method!=='bank_transfer')order=await markOrderPaid(id,'SANDBOX-'+Date.now());else order=(await db.query('SELECT * FROM orders WHERE id=$1',[id])).rows[0];await audit(u,'order.created','order',id,{paymentMethod:method,totalCents:total,coupon:cp.code||null});return json(res,201,{order,requiresGateway:order.status==='pending_gateway',bankInstructions:method==='bank_transfer'?(process.env.BANK_TRANSFER_INSTRUCTIONS||'Contact EduQuinn support for bank transfer details.'):null})}catch(e){return e.status?json(res,e.status,{message:e.message}):dbFail(res,e)}}
  if(url.pathname==='/api/orders/mine'&&req.method==='GET'){const u=await requireRole(req,res,['student']);if(!u)return;try{const r=await db.query(`SELECT o.*,COALESCE(json_agg(json_build_object('courseId',c.id,'title',c.title,'priceCents',oi.unit_price_cents)) FILTER (WHERE c.id IS NOT NULL),'[]') items FROM orders o LEFT JOIN order_items oi ON oi.order_id=o.id LEFT JOIN courses c ON c.id=oi.course_id WHERE o.student_id=$1 GROUP BY o.id ORDER BY o.created_at DESC`,[u.id]);return json(res,200,{orders:r.rows})}catch(e){return dbFail(res,e)}}
  const refundReq=url.pathname.match(/^\/api\/orders\/([0-9a-f-]{36})\/refund-request$/i);if(refundReq&&req.method==='POST'){const u=await requireRole(req,res,['student']);if(!u)return;try{const o=await db.query("SELECT * FROM orders WHERE id=$1 AND student_id=$2 AND status='paid'",[refundReq[1],u.id]);if(!o.rowCount)return json(res,404,{message:'A paid order eligible for refund was not found.'});const x=await readJson(req),id=uuid();await db.query('INSERT INTO refunds(id,order_id,requested_by,amount_cents,reason) VALUES($1,$2,$3,$4,$5)',[id,refundReq[1],u.id,o.rows[0].total_cents,String(x.reason||'').slice(0,2000)]);await db.query("UPDATE orders SET status='refund_requested',updated_at=now() WHERE id=$1",[refundReq[1]]);await audit(u,'refund.requested','order',refundReq[1],{});return json(res,201,{refundId:id,status:'requested'})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/instructor/earnings'&&req.method==='GET'){const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const instructorId=u.role==='admin'&&url.searchParams.get('instructorId')?url.searchParams.get('instructorId'):u.id;const sum=await db.query(`SELECT COALESCE(sum(instructor_cents) FILTER(WHERE status='available'),0) available,COALESCE(sum(instructor_cents) FILTER(WHERE status='pending'),0) pending,COALESCE(sum(instructor_cents),0) lifetime FROM instructor_earnings WHERE instructor_id=$1`,[instructorId]);const rows=await db.query(`SELECT e.*,c.title course_title,o.created_at order_date FROM instructor_earnings e JOIN courses c ON c.id=e.course_id JOIN orders o ON o.id=e.order_id WHERE e.instructor_id=$1 ORDER BY e.created_at DESC LIMIT 100`,[instructorId]);const payouts=await db.query('SELECT * FROM instructor_payouts WHERE instructor_id=$1 ORDER BY created_at DESC LIMIT 50',[instructorId]);return json(res,200,{summary:sum.rows[0],earnings:rows.rows,payouts:payouts.rows,sharePercent:INSTRUCTOR_SHARE_PERCENT})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/admin/orders'&&req.method==='GET'){const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query(`SELECT o.*,u.name student_name,u.email student_email,(SELECT count(*) FROM refunds f WHERE f.order_id=o.id AND f.status='requested') refund_requests FROM orders o JOIN users u ON u.id=o.student_id ORDER BY o.created_at DESC LIMIT 500`);return json(res,200,{orders:r.rows})}catch(e){return dbFail(res,e)}}
  const approveRefund=url.pathname.match(/^\/api\/admin\/refunds\/([0-9a-f-]{36})\/(approve|reject)$/i);if(approveRefund&&req.method==='POST'){const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query('SELECT * FROM refunds WHERE id=$1',[approveRefund[1]]);if(!r.rowCount)return json(res,404,{message:'Refund not found.'});const action=approveRefund[2],x=await readJson(req);if(action==='reject'){await db.query("UPDATE refunds SET status='rejected',admin_note=$2,resolved_at=now() WHERE id=$1",[approveRefund[1],String(x.note||'')]);await db.query("UPDATE orders SET status='paid',updated_at=now() WHERE id=$1",[r.rows[0].order_id]);}else{await db.query("UPDATE refunds SET status='processed',admin_note=$2,resolved_at=now() WHERE id=$1",[approveRefund[1],String(x.note||'')]);await db.query("UPDATE orders SET status='refunded',updated_at=now() WHERE id=$1",[r.rows[0].order_id]);await db.query("UPDATE instructor_earnings SET status='reversed' WHERE order_id=$1 AND status<>'paid'",[r.rows[0].order_id]);}await audit(u,'refund.'+(action==='approve'?'processed':'rejected'),'order',r.rows[0].order_id,{});return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/admin/refunds'&&req.method==='GET'){const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query(`SELECT f.*,o.student_id,o.total_cents,u.name student_name,u.email student_email FROM refunds f JOIN orders o ON o.id=f.order_id LEFT JOIN users u ON u.id=o.student_id ORDER BY f.created_at DESC LIMIT 300`);return json(res,200,{refunds:r.rows})}catch(e){return dbFail(res,e)}}
  const markPaid=url.pathname.match(/^\/api\/admin\/orders\/([0-9a-f-]{36})\/mark-paid$/i);if(markPaid&&req.method==='POST'){const u=await requireRole(req,res,['admin']);if(!u)return;try{const x=await readJson(req),order=await markOrderPaid(markPaid[1],String(x.reference||'MANUAL-'+Date.now()));await audit(u,'payment.confirmed','order',markPaid[1],{reference:x.reference||null});return json(res,200,{order})}catch(e){return e.status?json(res,e.status,{message:e.message}):dbFail(res,e)}}
  if(url.pathname==='/api/admin/payouts/approve'&&req.method==='POST'){const u=await requireRole(req,res,['admin']);if(!u)return;try{const x=await readJson(req),instructorId=String(x.instructorId||'');if(!/^[0-9a-f-]{36}$/i.test(instructorId))return json(res,400,{message:'A valid instructor is required.'});const available=await db.query("SELECT COALESCE(sum(instructor_cents),0) total,min(created_at)::date period_start,max(created_at)::date period_end FROM instructor_earnings WHERE instructor_id=$1 AND status='available' AND payout_id IS NULL",[instructorId]);const amount=Number(available.rows[0].total||0);if(amount<=0)return json(res,409,{message:'This instructor has no available earnings.'});const id=uuid(),ref='EQ-PAYOUT-'+Date.now().toString().slice(-10);await db.query("INSERT INTO instructor_payouts(id,instructor_id,amount_cents,currency,status,reference,period_start,period_end,paid_at) VALUES($1,$2,$3,'USD','paid',$4,$5,$6,now())",[id,instructorId,amount,ref,available.rows[0].period_start,available.rows[0].period_end]);await db.query("UPDATE instructor_earnings SET status='paid',payout_id=$1 WHERE instructor_id=$2 AND status='available' AND payout_id IS NULL",[id,instructorId]);await audit(u,'payout.approved','user',instructorId,{payoutId:id,amountCents:amount});return json(res,201,{payout:{id,reference:ref,amountCents:amount,status:'paid'}})}catch(e){return dbFail(res,e)}}
  if(url.pathname==='/api/admin/coupons'&&req.method==='POST'){const u=await requireRole(req,res,['admin']);if(!u)return;try{const x=await readJson(req),code=String(x.code||'').trim().toUpperCase();if(!/^[A-Z0-9_-]{3,30}$/.test(code))return json(res,400,{message:'Use a coupon code of 3–30 letters, numbers, _ or -.'});const type=x.discountType==='fixed'?'fixed':'percent',value=type==='percent'?Math.min(100,Math.max(1,Number(x.discountValue||0))):Math.max(1,Number(x.discountValue||0));const id=uuid();await db.query('INSERT INTO coupons(id,code,description,discount_type,discount_value,max_redemptions,created_by,ends_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',[id,code,String(x.description||''),type,Math.round(value),x.maxRedemptions?Number(x.maxRedemptions):null,u.id,x.endsAt||null]);return json(res,201,{id,code})}catch(e){return dbFail(res,e)}}


  // Phase 13 live learning
  if(url.pathname==='/api/live-classes'&&req.method==='GET'){
    try{const viewer=await currentUser(req);const r=await db.query(`SELECT l.id,l.course_id,l.title,l.description,l.starts_at,l.duration_minutes,l.capacity,l.status,l.session_type,l.timezone,l.meeting_provider,l.recording_url,l.recording_status,u.name instructor_name,c.title course_title,(SELECT count(*)::int FROM live_class_attendance a WHERE a.live_class_id=l.id) reserved_count FROM live_classes l JOIN users u ON u.id=l.instructor_id LEFT JOIN courses c ON c.id=l.course_id WHERE l.status<>'cancelled' ORDER BY CASE WHEN l.status='live' THEN 0 WHEN l.status='upcoming' THEN 1 ELSE 2 END,l.starts_at ASC LIMIT 250`);let reserved=new Set();if(viewer?.role==='student'){const a=await db.query('SELECT live_class_id FROM live_class_attendance WHERE student_id=$1',[viewer.id]);reserved=new Set(a.rows.map(x=>x.live_class_id))}return json(res,200,{classes:r.rows.map(x=>({...x,reserved:reserved.has(x.id)}))})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/instructor/live-classes'&&req.method==='GET'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const params=[],where=u.role==='instructor'?'WHERE l.instructor_id=$1':'';if(u.role==='instructor')params.push(u.id);const r=await db.query(`SELECT l.*,c.title course_title,(SELECT count(*)::int FROM live_class_attendance a WHERE a.live_class_id=l.id) reserved_count,(SELECT count(*)::int FROM live_class_attendance a WHERE a.live_class_id=l.id AND a.joined_at IS NOT NULL) attended_count FROM live_classes l LEFT JOIN courses c ON c.id=l.course_id ${where} ORDER BY l.starts_at DESC LIMIT 300`,params);return json(res,200,{classes:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/instructor/live-classes'&&req.method==='POST'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const x=await readJson(req),title=String(x.title||'').trim(),description=String(x.description||'').trim(),courseId=String(x.courseId||'').trim()||null,startsAt=new Date(x.startsAt),duration=Math.max(10,Math.min(480,Number(x.durationMinutes||60))),capacity=Math.max(1,Math.min(10000,Number(x.capacity||50))),provider=String(x.meetingProvider||'external').trim().slice(0,40),joinUrl=String(x.joinUrl||'').trim(),hostUrl=String(x.hostUrl||'').trim(),sessionType=String(x.sessionType||'group_class').trim().slice(0,40),timezone=String(x.timezone||'Africa/Harare').trim().slice(0,80),allowRecording=!!x.allowRecording;if(title.length<3||Number.isNaN(startsAt.getTime())||startsAt.getTime()<Date.now()-300000)return json(res,400,{message:'Enter a valid title and future class date/time.'});if(courseId&&!/^[0-9a-f-]{36}$/i.test(courseId))return json(res,400,{message:'Invalid course.'});if(courseId&&u.role==='instructor'&&!(await ownsCourse(u,courseId)))return json(res,403,{message:'You can only schedule classes for your own courses.'});if(joinUrl&&!/^https:\/\//i.test(joinUrl))return json(res,400,{message:'The student meeting URL must use https://'});if(hostUrl&&!/^https:\/\//i.test(hostUrl))return json(res,400,{message:'The host meeting URL must use https://'});const id=uuid();await db.query(`INSERT INTO live_classes(id,instructor_id,course_id,title,description,starts_at,duration_minutes,capacity,status,meeting_provider,meeting_ref,session_type,timezone,join_url,host_url,allow_recording) VALUES($1,$2,$3,$4,$5,$6,$7,$8,'upcoming',$9,$10,$11,$12,$13,$14,$15)`,[id,u.id,courseId,title,description,startsAt.toISOString(),duration,capacity,provider,String(x.meetingRef||'').slice(0,300),sessionType,timezone,joinUrl||null,hostUrl||null,allowRecording]);if(courseId){try{const learners=await db.query('SELECT student_id FROM enrolments WHERE course_id=$1',[courseId]);for(const row of learners.rows){await notifyUser(row.student_id,{type:'live_class',title:'New live class scheduled',body:`${title} is scheduled for ${startsAt.toLocaleString('en-ZW',{timeZone:'Africa/Harare'})}.`,link:'/live-classes.html',emailSubject:`EduQuinn live class: ${title}`,emailText:`A new live class has been scheduled.\n\n${title}\n${startsAt.toLocaleString('en-ZW',{timeZone:'Africa/Harare'})}\n\nSign in to reserve your seat.`,channel:'live_classes'})}}catch{}}await audit(u,'live_class.created','live_class',id,{title,courseId});return json(res,201,{id})}catch(e){return e.status?json(res,e.status,{message:e.message}):dbFail(res,e)}
  }
  const reserveLive=url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/reserve$/i);if(reserveLive&&req.method==='POST'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{const l=await db.query('SELECT * FROM live_classes WHERE id=$1',[reserveLive[1]]);if(!l.rowCount)return json(res,404,{message:'Live class not found.'});const c=l.rows[0];if(!['upcoming','live'].includes(c.status))return json(res,409,{message:'Reservations are closed for this class.'});if(c.course_id){const en=await db.query('SELECT 1 FROM enrolments WHERE student_id=$1 AND course_id=$2',[u.id,c.course_id]);if(!en.rowCount)return json(res,403,{message:'Enroll in the related course before reserving this live class.'})}const count=await db.query('SELECT count(*)::int n FROM live_class_attendance WHERE live_class_id=$1',[c.id]);if(Number(count.rows[0].n)>=Number(c.capacity))return json(res,409,{message:'This live class is full.'});await db.query('INSERT INTO live_class_attendance(live_class_id,student_id) VALUES($1,$2) ON CONFLICT(live_class_id,student_id) DO NOTHING',[c.id,u.id]);await notifyUser(u.id,{type:'live_reservation',title:'Live class reserved',body:`Your seat for ${c.title} is reserved.`,link:'/live-classes.html',emailSubject:`EduQuinn reservation confirmed: ${c.title}`,emailText:`Your seat for ${c.title} has been reserved. Sign in to EduQuinn before the session to join.`,channel:'live_classes'});return json(res,200,{reserved:true})}catch(e){return dbFail(res,e)}
  }
  if(reserveLive&&req.method==='DELETE'){
    const u=await requireRole(req,res,['student']);if(!u)return;try{await db.query('DELETE FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2 AND joined_at IS NULL',[reserveLive[1],u.id]);return json(res,200,{reserved:false})}catch(e){return dbFail(res,e)}
  }
  const joinLive=url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/join$/i);if(joinLive&&req.method==='POST'){
    const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{const r=await db.query('SELECT * FROM live_classes WHERE id=$1',[joinLive[1]]);if(!r.rowCount)return json(res,404,{message:'Live class not found.'});const l=r.rows[0];if(l.status==='cancelled'||l.status==='completed')return json(res,409,{message:'This live class is not open.'});const startMs=new Date(l.starts_at).getTime(),endMs=startMs+Number(l.duration_minutes||60)*60000,now=Date.now();if(u.role==='student'){if(now<startMs-30*60000)return json(res,409,{message:'This classroom opens 30 minutes before the scheduled start.'});if(now>endMs+60*60000)return json(res,409,{message:'This live class has ended.'});const a=await db.query('SELECT 1 FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2',[l.id,u.id]);if(!a.rowCount)return json(res,403,{message:'Reserve your seat before joining.'});await db.query(`UPDATE live_class_attendance SET joined_at=COALESCE(joined_at,now()),last_joined_at=now(),join_count=join_count+1 WHERE live_class_id=$1 AND student_id=$2`,[l.id,u.id])}else if(u.role==='instructor'&&l.instructor_id!==u.id)return json(res,403,{message:'This class belongs to another instructor.'});if(l.status==='upcoming'&&now>=startMs-30*60000)await db.query("UPDATE live_classes SET status='live',updated_at=now() WHERE id=$1",[l.id]);const target=u.role==='instructor'?(l.host_url||l.join_url):(l.join_url||null);return json(res,200,{classId:l.id,title:l.title,joinUrl:target,provider:l.meeting_provider||'external',roomUrl:`/live-class-room.html?id=${l.id}`})}catch(e){return dbFail(res,e)}
  }
  const leaveLive=url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/leave$/i);if(leaveLive&&req.method==='POST'){
    const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{if(u.role==='student'){await db.query(`UPDATE live_class_attendance SET attendance_minutes=attendance_minutes+GREATEST(0,LEAST(480,round(extract(epoch from (now()-COALESCE(last_joined_at,joined_at)))/60)::int)),left_at=now() WHERE live_class_id=$1 AND student_id=$2`,[leaveLive[1],u.id])}return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}
  }
  const attendanceLive=url.pathname.match(/^\/api\/instructor\/live-classes\/([0-9a-f-]{36})\/attendance$/i);if(attendanceLive&&req.method==='GET'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const l=await db.query('SELECT * FROM live_classes WHERE id=$1',[attendanceLive[1]]);if(!l.rowCount)return json(res,404,{message:'Live class not found.'});if(u.role==='instructor'&&l.rows[0].instructor_id!==u.id)return json(res,403,{message:'This class belongs to another instructor.'});const r=await db.query(`SELECT a.*,s.name student_name,s.email student_email FROM live_class_attendance a JOIN users s ON s.id=a.student_id WHERE a.live_class_id=$1 ORDER BY s.name`,[attendanceLive[1]]);return json(res,200,{attendance:r.rows})}catch(e){return dbFail(res,e)}
  }
  const updateLive=url.pathname.match(/^\/api\/instructor\/live-classes\/([0-9a-f-]{36})$/i);if(updateLive&&req.method==='PATCH'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const l=await db.query('SELECT * FROM live_classes WHERE id=$1',[updateLive[1]]);if(!l.rowCount)return json(res,404,{message:'Live class not found.'});if(u.role==='instructor'&&l.rows[0].instructor_id!==u.id)return json(res,403,{message:'This class belongs to another instructor.'});const x=await readJson(req),status=['upcoming','live','completed','cancelled'].includes(x.status)?x.status:null,recordingUrl=String(x.recordingUrl||'').trim();if(recordingUrl&&!/^https:\/\//i.test(recordingUrl))return json(res,400,{message:'Recording URL must use https://'});await db.query(`UPDATE live_classes SET status=COALESCE($2,status),recording_url=COALESCE(NULLIF($3,''),recording_url),recording_status=CASE WHEN NULLIF($3,'') IS NOT NULL THEN 'ready' ELSE recording_status END,ended_at=CASE WHEN $2='completed' THEN now() ELSE ended_at END,cancelled_at=CASE WHEN $2='cancelled' THEN now() ELSE cancelled_at END,updated_at=now() WHERE id=$1`,[updateLive[1],status,recordingUrl]);await audit(u,'live_class.updated','live_class',updateLive[1],{status,recordingUrl:!!recordingUrl});return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}
  }
  if(updateLive&&req.method==='DELETE'){
    const u=await requireRole(req,res,['instructor','admin']);if(!u)return;try{const l=await db.query('SELECT * FROM live_classes WHERE id=$1',[updateLive[1]]);if(!l.rowCount)return json(res,404,{message:'Live class not found.'});if(u.role==='instructor'&&l.rows[0].instructor_id!==u.id)return json(res,403,{message:'This class belongs to another instructor.'});await db.query("UPDATE live_classes SET status='cancelled',cancelled_at=now(),updated_at=now() WHERE id=$1",[updateLive[1]]);return json(res,200,{ok:true})}catch(e){return dbFail(res,e)}
  }
  const liveMessages=url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/messages$/i);if(liveMessages&&req.method==='GET'){
    const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{const live=await db.query('SELECT instructor_id FROM live_classes WHERE id=$1',[liveMessages[1]]);if(!live.rowCount)return json(res,404,{message:'Live class not found.'});if(u.role==='student'){const allowed=await db.query('SELECT 1 FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2',[liveMessages[1],u.id]);if(!allowed.rowCount)return json(res,403,{message:'Reserve this class before using its chat.'})}else if(u.role==='instructor'&&live.rows[0].instructor_id!==u.id)return json(res,403,{message:'This class belongs to another instructor.'});const r=await db.query(`SELECT m.id,m.message,m.created_at,u.name,u.role FROM live_class_messages m JOIN users u ON u.id=m.user_id WHERE m.live_class_id=$1 ORDER BY m.created_at ASC LIMIT 300`,[liveMessages[1]]);return json(res,200,{messages:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(liveMessages&&req.method==='POST'){
    const u=await requireRole(req,res,['student','instructor','admin']);if(!u)return;try{const live=await db.query('SELECT instructor_id FROM live_classes WHERE id=$1',[liveMessages[1]]);if(!live.rowCount)return json(res,404,{message:'Live class not found.'});if(u.role==='student'){const allowed=await db.query('SELECT 1 FROM live_class_attendance WHERE live_class_id=$1 AND student_id=$2',[liveMessages[1],u.id]);if(!allowed.rowCount)return json(res,403,{message:'Reserve this class before using its chat.'})}else if(u.role==='instructor'&&live.rows[0].instructor_id!==u.id)return json(res,403,{message:'This class belongs to another instructor.'});const x=await readJson(req),message=String(x.message||'').trim();if(!message||message.length>1500)return json(res,400,{message:'Enter a message of up to 1,500 characters.'});await db.query('INSERT INTO live_class_messages(id,live_class_id,user_id,message) VALUES($1,$2,$3,$4)',[uuid(),liveMessages[1],u.id,message]);return json(res,201,{ok:true})}catch(e){return dbFail(res,e)}
  }
  const calendarLive=url.pathname.match(/^\/api\/live-classes\/([0-9a-f-]{36})\/calendar\.ics$/i);if(calendarLive&&req.method==='GET'){
    try{const r=await db.query(`SELECT l.*,u.name instructor_name FROM live_classes l JOIN users u ON u.id=l.instructor_id WHERE l.id=$1`,[calendarLive[1]]);if(!r.rowCount){res.writeHead(404,securityHeaders);return res.end('Not found')}const l=r.rows[0],start=new Date(l.starts_at),end=new Date(start.getTime()+Number(l.duration_minutes)*60000),fmt=d=>d.toISOString().replace(/[-:]/g,'').replace(/\.\d{3}Z$/,'Z'),safe=v=>String(v||'').replace(/[\\;,\n]/g,m=>({'\\':'\\\\',';':'\\;',',':'\\,','\n':'\\n'}[m]||''));const ics=`BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//EduQuinn//Live Classes//EN\r\nBEGIN:VEVENT\r\nUID:${l.id}@eduquinn\r\nDTSTAMP:${fmt(new Date())}\r\nDTSTART:${fmt(start)}\r\nDTEND:${fmt(end)}\r\nSUMMARY:${safe(l.title)}\r\nDESCRIPTION:${safe((l.description||'')+' Instructor: '+l.instructor_name)}\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n`;res.writeHead(200,{...securityHeaders,'Content-Type':'text/calendar; charset=utf-8','Content-Disposition':`attachment; filename="eduquinn-live-class.ics"`});return res.end(ics)}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/system-health'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const database=await db.ping();const tables=await db.query(`SELECT (SELECT count(*)::int FROM users) users,(SELECT count(*)::int FROM courses) courses,(SELECT count(*)::int FROM enrolments) enrolments,(SELECT count(*)::int FROM orders) orders,(SELECT count(*)::int FROM certificates) certificates,(SELECT count(*)::int FROM sessions WHERE expires_at>now()) active_sessions,(SELECT count(*)::int FROM security_events WHERE created_at>now()-interval '24 hours') security_events_24h`);const launch=(await db.query('SELECT status,registrations_open,instructor_applications_open,public_certificate_verification,launched_at FROM platform_launch_state WHERE id=1')).rows[0];const disk=fs.statSync(MEDIA_ROOT);return json(res,200,{phase:'16',environment:NODE_ENV,database,counts:tables.rows[0],launch,configuration:{appBaseUrl:!!APP_BASE_URL,emailConfigured:!!BREVO_API_KEY,paymentSandbox:PAYMENT_SANDBOX,mediaRoot:MEDIA_ROOT,allowedOrigins:[...ALLOWED_ORIGINS]},uptimeSeconds:Math.round(process.uptime()),memory:process.memoryUsage(),mediaRootAvailable:disk.isDirectory()})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/platform/status'&&req.method==='GET'){
    try{const r=await db.query('SELECT status,registrations_open,instructor_applications_open,public_certificate_verification,launch_message,launched_at,updated_at FROM platform_launch_state WHERE id=1');return json(res,200,{platform:r.rows[0]||{status:'prelaunch',registrations_open:true,instructor_applications_open:true,public_certificate_verification:true}})}catch(e){return dbFail(res,e)}
  }
  const verifyCert=url.pathname.match(/^\/api\/certificates\/verify\/([^/]+)$/i);
  if(verifyCert&&req.method==='GET'){
    try{const state=(await db.query('SELECT public_certificate_verification FROM platform_launch_state WHERE id=1')).rows[0];if(state&&state.public_certificate_verification===false)return json(res,403,{message:'Public certificate verification is currently unavailable.'});const code=decodeURIComponent(verifyCert[1]).trim().toUpperCase();const r=await db.query(`SELECT ce.certificate_code,ce.issued_at,u.name student_name,c.title course_title,c.education_level,c.category,i.name instructor_name FROM certificates ce JOIN users u ON u.id=ce.student_id JOIN courses c ON c.id=ce.course_id LEFT JOIN users i ON i.id=c.instructor_id WHERE upper(ce.certificate_code)=upper($1)`,[code]);if(!r.rowCount)return json(res,404,{valid:false,message:'Certificate not found.'});return json(res,200,{valid:true,certificate:r.rows[0]})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/launch-state'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query('SELECT * FROM platform_launch_state WHERE id=1');return json(res,200,{platform:r.rows[0]})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/launch-state'&&req.method==='POST'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const x=await readJson(req),status=['prelaunch','live','maintenance'].includes(x.status)?x.status:'prelaunch',registrations=!!x.registrationsOpen,instructors=!!x.instructorApplicationsOpen,verify=x.publicCertificateVerification!==false,message=String(x.launchMessage||'').trim().slice(0,300)||'EduQuinn is preparing for launch.';const r=await db.query(`UPDATE platform_launch_state SET status=$1,registrations_open=$2,instructor_applications_open=$3,public_certificate_verification=$4,launch_message=$5,launched_at=CASE WHEN $1='live' THEN COALESCE(launched_at,now()) ELSE launched_at END,updated_by=$6,updated_at=now() WHERE id=1 RETURNING *`,[status,registrations,instructors,verify,message,u.id]);await audit(u,'platform.launch_state_updated','platform','1',{status,registrationsOpen:registrations,instructorApplicationsOpen:instructors,publicCertificateVerification:verify});return json(res,200,{platform:r.rows[0]})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/support'&&req.method==='POST')return receiveSupport(req,res);
  if(url.pathname==='/api/admin/courses'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query(`SELECT c.id,c.title,c.category,c.education_level,c.price_cents,c.status,c.quality_score,c.created_at,c.updated_at,i.name instructor_name,i.email instructor_email FROM courses c LEFT JOIN users i ON i.id=c.instructor_id ORDER BY c.updated_at DESC LIMIT 500`);return json(res,200,{courses:r.rows})}catch(e){return dbFail(res,e)}
  }
  const adminCourseStatus=url.pathname.match(/^\/api\/admin\/courses\/([0-9a-f-]{36})\/status$/i);
  if(adminCourseStatus&&req.method==='POST'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const x=await readJson(req),status=String(x.status||'');if(!['draft','under_review','changes_requested','published','suspended','archived'].includes(status))return json(res,400,{message:'Invalid course status.'});const q=await db.query('UPDATE courses SET status=$2,updated_at=now() WHERE id=$1 RETURNING id,title,status',[adminCourseStatus[1],status]);if(!q.rowCount)return json(res,404,{message:'Course not found.'});await audit(u,'course.status_updated','course',adminCourseStatus[1],{status});return json(res,200,{course:q.rows[0]})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/payouts'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query(`SELECT i.id instructor_id,i.name,i.email,COALESCE(sum(e.gross_cents),0)::bigint gross_cents,COALESCE(sum(e.instructor_cents) FILTER (WHERE e.status='available'),0)::bigint available_cents,COALESCE(sum(e.instructor_cents) FILTER (WHERE e.status='pending'),0)::bigint pending_cents,COALESCE(sum(e.instructor_cents) FILTER (WHERE e.status='paid'),0)::bigint paid_cents FROM users i LEFT JOIN instructor_earnings e ON e.instructor_id=i.id WHERE i.role='instructor' GROUP BY i.id,i.name,i.email ORDER BY available_cents DESC,i.name`);return json(res,200,{payouts:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/reviews'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query(`SELECT r.id,r.rating,r.review,r.status,r.created_at,s.name student_name,c.title course_title FROM reviews r JOIN users s ON s.id=r.student_id JOIN courses c ON c.id=r.course_id ORDER BY r.created_at DESC LIMIT 500`);return json(res,200,{reviews:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/users'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query('SELECT id,name,email,role,status,expertise,email_verified,created_at FROM users ORDER BY created_at DESC LIMIT 500');return json(res,200,{users:r.rows})}catch(e){return dbFail(res,e)}
  }
  if(url.pathname==='/api/admin/audit'&&req.method==='GET'){
    const u=await requireRole(req,res,['admin']);if(!u)return;try{const r=await db.query(`SELECT a.*,u.name actor_name,u.email actor_email FROM audit_log a LEFT JOIN users u ON u.id=a.actor_user_id ORDER BY a.created_at DESC LIMIT 300`);return json(res,200,{events:r.rows})}catch(e){return dbFail(res,e)}
  }
  return json(res,404,{message:'API route not found.'});
}

async function receiveSupport(req,res){
  try{const data=await readJson(req,25000),clean=v=>String(v||'').trim();const name=clean(data.name),email=clean(data.email),role=clean(data.role),topic=clean(data.topic),subject=clean(data.subject),message=clean(data.message);if(!name||!validEmail(email)||!subject||!message)return json(res,400,{message:'Please complete all required support fields.'});let user=null;try{user=await currentUser(req)}catch{}const ticketId=uuid();try{await db.query('INSERT INTO support_tickets(id,user_id,name,email,role,topic,subject,message) VALUES($1,$2,$3,$4,$5,$6,$7,$8)',[ticketId,user?.id||null,name,email,role,topic,subject,message])}catch(e){if(e.code!=='DB_NOT_CONFIGURED')throw e}
    if(!BREVO_API_KEY)return json(res,202,{message:'Your support request has been recorded.',ticketId,emailDelivery:'not_configured',fallback:true,email:SUPPORT_EMAIL});
    const text=`Ticket: ${ticketId}\nName: ${name}\nEmail: ${email}\nRole: ${role}\nTopic: ${topic}\n\n${message}`;const payload=JSON.stringify({sender:{name:'EduQuinn Support Form',email:SUPPORT_FROM_EMAIL},to:[{email:SUPPORT_EMAIL,name:'EduQuinn Support'}],replyTo:{email,name},subject:`[EduQuinn Support] ${topic} — ${subject}`,textContent:text});
    const status=await new Promise(resolve=>{const r=https.request({hostname:'api.brevo.com',path:'/v3/smtp/email',method:'POST',headers:{'api-key':BREVO_API_KEY,'Content-Type':'application/json','Content-Length':Buffer.byteLength(payload)}},br=>{br.resume();br.on('end',()=>resolve(br.statusCode))});r.on('error',()=>resolve(0));r.write(payload);r.end()});
    return status>=200&&status<300?json(res,200,{message:'Your support request has been sent to EduQuinn Support.',ticketId}):json(res,202,{message:'Your support request was recorded, but email delivery is temporarily unavailable.',ticketId,fallback:true,email:SUPPORT_EMAIL});
  }catch(e){return e.status?json(res,e.status,{message:e.message}):dbFail(res,e)}
}
function stream(filePath,res,req){fs.readFile(filePath,(err,data)=>{if(err){res.writeHead(404,securityHeaders);return res.end('Not found')}const ext=path.extname(filePath),mime=types[ext]||'application/octet-stream',name=path.basename(filePath),noCache=ext==='.html'||name==='service-worker.js'||name==='manifest.webmanifest',headers={...securityFor(req),'Content-Type':mime,'Cache-Control':noCache?'no-cache':'public, max-age=3600'};const accepts=String(req.headers['accept-encoding']||'');if(/gzip/.test(accepts)&&/^(text\/|application\/(javascript|json))/.test(mime)&&data.length>1024){zlib.gzip(data,(e,gz)=>{if(e){res.writeHead(200,headers);return res.end(data)}res.writeHead(200,{...headers,'Content-Encoding':'gzip','Vary':'Accept-Encoding'});res.end(gz)});return}res.writeHead(200,headers);res.end(data)})}
const server=http.createServer(async(req,res)=>{const rid=requestId(req);res.setHeader('X-Request-Id',rid);const started=Date.now();res.on('finish',()=>{if(NODE_ENV!=='test')console.log(JSON.stringify({time:new Date().toISOString(),requestId:rid,method:req.method,url:req.url,status:res.statusCode,ms:Date.now()-started,ip:clientIp(req)}))});try{const url=new URL(req.url,`http://${req.headers.host||'localhost'}`);if(url.pathname==='/health')return json(res,200,{status:'ok',phase:'16',uptimeSeconds:Math.round(process.uptime())});if(url.pathname==='/ready'){const database=await db.ping();return json(res,database.ok?200:503,{status:database.ok?'ready':'not_ready',phase:'16',database})}if(url.pathname.startsWith('/api/')){if(!requireApiGuard(req,res,url))return;return api(req,res,url)}let requested=url.pathname==='/'?'/index.html':decodeURIComponent(url.pathname);let filePath=path.normalize(path.join(PUBLIC,requested));if(!filePath.startsWith(PUBLIC)){res.writeHead(403,securityFor(req));return res.end('Forbidden')}fs.stat(filePath,(err,stat)=>{if(!err&&stat.isFile())return stream(filePath,res,req);stream(path.join(PUBLIC,'index.html'),res,req)})}catch(e){console.error(rid,e);json(res,500,{message:'Unexpected server error.',requestId:rid})}});
server.keepAliveTimeout=65000;server.headersTimeout=66000;server.requestTimeout=120000;
let shuttingDown=false;async function shutdown(signal){if(shuttingDown)return;shuttingDown=true;console.log(`${signal}: graceful shutdown started`);server.close(async()=>{try{if(db.pool)await db.pool.end()}catch{}process.exit(0)});setTimeout(()=>process.exit(1),10000).unref()}
process.on('SIGTERM',()=>shutdown('SIGTERM'));process.on('SIGINT',()=>shutdown('SIGINT'));process.on('unhandledRejection',e=>console.error('Unhandled rejection',e));process.on('uncaughtException',e=>{console.error('Uncaught exception',e);shutdown('uncaughtException')});
(async()=>{try{if(process.env.DATABASE_URL){await db.migrate();await db.query('DELETE FROM sessions WHERE expires_at<=now()').catch(()=>{});console.log('PostgreSQL schema ready.')}else console.warn('DATABASE_URL is not set. Static frontend will run, database APIs will return 503.')}catch(e){console.error('Database migration failed:',e.message)}server.listen(PORT,()=>console.log(`EduQuinn Phase 16 running on port ${PORT}`))})();
