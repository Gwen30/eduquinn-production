const fs=require('fs');
const path=require('path');
const {Pool}=require('pg');
const url=process.env.DATABASE_URL||'';
// On Supabase, DATABASE_SSL should be "true" and DATABASE_URL should be the
// **Transaction pooler** connection string (port 6543) with
// "?workaround=supabase-pooler.vercel" appended — that query param is
// Supabase's own documented requirement for Vercel serverless functions, so
// each short-lived invocation doesn't exhaust Postgres' direct connection
// limit. See Supabase dashboard → Project Settings → Database → Connect.
//
// rejectUnauthorized:false (the original setting) disables certificate
// validation entirely. Supabase publishes its CA bundle, so prefer real
// validation; set PGSSLROOTCERT to a path containing Supabase's CA
// certificate (download from the dashboard) to get rejectUnauthorized:true
// without this falling back to an insecure connection. If PGSSLROOTCERT is
// not set, this still works (matching the previous Render behavior) but
// without certificate validation — fine for getting unblocked, worth
// tightening once the migration is stable.
const ssl=/^(1|true|yes)$/i.test(process.env.DATABASE_SSL||'')
  ? (process.env.PGSSLROOTCERT
      ? {ca:fs.readFileSync(process.env.PGSSLROOTCERT,'utf8'),rejectUnauthorized:true}
      : {rejectUnauthorized:false})
  : undefined;
// Serverless note: each Vercel function invocation can get its own module
// instance (though warm invocations reuse it), and many can run
// concurrently. A pool max of 10 (fine for one long-running Render process)
// would multiply badly across concurrent invocations and exhaust Supabase's
// connection pooler. Keep this small — the Supabase Transaction pooler is
// itself already pooling many app-side connections down to a smaller number
// of real Postgres connections, so a small per-invocation pool here is both
// sufficient and correct.
const pool=url?new Pool({connectionString:url,ssl,max:Number(process.env.DB_POOL_MAX||3),idleTimeoutMillis:10000}):null;
async function query(text,params=[]){if(!pool) throw Object.assign(new Error('DATABASE_URL is not configured'),{code:'DB_NOT_CONFIGURED'});return pool.query(text,params)}
// migrate() is NOT called automatically anywhere in the Vercel/serverless
// setup (unlike the old server.js boot sequence). Run `npm run migrate`
// manually (or from a CI step) against Supabase after a schema.sql change —
// running arbitrary DDL as a side effect of a cold start is unsafe when many
// invocations can start concurrently.
async function migrate(){if(!pool)return false;const sql=fs.readFileSync(path.join(__dirname,'db','schema.sql'),'utf8');await pool.query(sql);return true}
async function ping(){if(!pool)return {configured:false,ok:false};try{await pool.query('SELECT 1');return {configured:true,ok:true}}catch(e){return {configured:true,ok:false,error:e.message}}}
// Real transactions: pool.query('BEGIN') is unsafe because the pool may serve each call from a
// different client, so BEGIN/COMMIT/ROLLBACK would not actually wrap the same connection. This
// checks out ONE dedicated client for the whole callback, and always releases it back to the pool.
// This still works correctly against Supabase's Transaction-mode pooler: transaction mode holds
// one real connection for exactly the duration of a single client's transaction, which is exactly
// what pool.connect()+BEGIN...COMMIT+release() does here.
async function withTransaction(fn){
  if(!pool) throw Object.assign(new Error('DATABASE_URL is not configured'),{code:'DB_NOT_CONFIGURED'});
  const client=await pool.connect();
  try{
    await client.query('BEGIN');
    const result=await fn(client);
    await client.query('COMMIT');
    return result;
  }catch(e){
    try{await client.query('ROLLBACK')}catch{}
    throw e;
  }finally{
    client.release();
  }
}
module.exports={pool,query,migrate,ping,withTransaction};
