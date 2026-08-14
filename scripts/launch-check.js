const fs=require('fs');
const path=require('path');
const db=require('../db');
function check(name,ok,detail=''){console.log(`${ok?'PASS':'FAIL'}  ${name}${detail?` — ${detail}`:''}`);return !!ok}
(async()=>{let ok=true;
  const base=process.env.APP_BASE_URL||'';
  ok=check('Production mode',(process.env.NODE_ENV||'')==='production',process.env.NODE_ENV||'not set')&&ok;
  ok=check('HTTPS public URL',/^https:\/\//i.test(base),base||'not set')&&ok;
  ok=check('Database configured',!!process.env.DATABASE_URL)&&ok;
  ok=check('Payment sandbox disabled',!/^(1|true|yes)$/i.test(process.env.PAYMENT_SANDBOX||''))&&ok;
  check('Transactional email configured',!!process.env.BREVO_API_KEY,process.env.BREVO_API_KEY?'configured':'recommended before public launch');
  const media=path.resolve(process.env.MEDIA_ROOT||'./storage/media');try{fs.mkdirSync(media,{recursive:true});ok=check('Media storage writable',true,media)&&ok}catch(e){ok=check('Media storage writable',false,e.message)&&ok}
  const ping=await db.ping();ok=check('PostgreSQL reachable',ping.ok,ping.error||'')&&ok;
  if(ping.ok){
    const r=await db.query(`SELECT
      (SELECT count(*)::int FROM users WHERE role='admin' AND status='active') admins,
      (SELECT count(*)::int FROM users WHERE role='instructor' AND status='active') instructors,
      (SELECT count(*)::int FROM courses WHERE status='published') published_courses,
      (SELECT count(*)::int FROM platform_launch_state) launch_state,
      (SELECT status FROM platform_launch_state WHERE id=1) launch_status`);
    const x=r.rows[0];
    ok=check('Active administrator',Number(x.admins)>0,String(x.admins))&&ok;
    check('Active instructors',Number(x.instructors)>0,String(x.instructors));
    ok=check('Published course available',Number(x.published_courses)>0,String(x.published_courses))&&ok;
    ok=check('Phase 16 launch schema',Number(x.launch_state)===1,x.launch_status||'missing')&&ok;
  }
  if(db.pool)await db.pool.end();
  console.log(ok?'\nEduQuinn launch checks passed.':'\nLaunch blockers remain. Correct FAIL items before going live.');
  process.exit(ok?0:1);
})().catch(e=>{console.error(e);process.exit(1)});
