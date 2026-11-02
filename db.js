const fs=require('fs');
const path=require('path');
const {Pool}=require('pg');
const url=process.env.DATABASE_URL||'';
const ssl=/^(1|true|yes)$/i.test(process.env.DATABASE_SSL||'') ? {rejectUnauthorized:false} : undefined;
const pool=url?new Pool({connectionString:url,ssl,max:10,idleTimeoutMillis:30000}):null;
async function query(text,params=[]){if(!pool) throw Object.assign(new Error('DATABASE_URL is not configured'),{code:'DB_NOT_CONFIGURED'});return pool.query(text,params)}
async function migrate(){if(!pool)return false;const sql=fs.readFileSync(path.join(__dirname,'db','schema.sql'),'utf8');await pool.query(sql);return true}
async function ping(){if(!pool)return {configured:false,ok:false};try{await pool.query('SELECT 1');return {configured:true,ok:true}}catch(e){return {configured:true,ok:false,error:e.message}}}
// Real transactions: pool.query('BEGIN') is unsafe because the pool may serve each call from a
// different client, so BEGIN/COMMIT/ROLLBACK would not actually wrap the same connection. This
// checks out ONE dedicated client for the whole callback, and always releases it back to the pool.
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
