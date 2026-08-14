const {spawn}=require('child_process');
const fs=require('fs');
const path=require('path');
const zlib=require('zlib');
const url=process.env.DATABASE_URL;if(!url){console.error('DATABASE_URL is not configured.');process.exit(1)}
const dir=path.resolve(process.env.BACKUP_DIR||'./backups');fs.mkdirSync(dir,{recursive:true});
const stamp=new Date().toISOString().replace(/[:.]/g,'-');const file=path.join(dir,`eduquinn-${stamp}.sql.gz`);
const child=spawn(process.env.PG_DUMP_BIN||'pg_dump',['--no-owner','--no-privileges',url],{stdio:['ignore','pipe','pipe']});const gzip=zlib.createGzip({level:9}),out=fs.createWriteStream(file);let err='';child.stderr.on('data',c=>err+=c);child.stdout.pipe(gzip).pipe(out);child.on('error',e=>{console.error('Could not start pg_dump:',e.message);console.error('Install PostgreSQL client tools or set PG_DUMP_BIN.');process.exitCode=1});child.on('close',code=>{if(code!==0){console.error(err||`pg_dump exited with code ${code}`);try{fs.unlinkSync(file)}catch{}process.exit(1)}console.log(`Backup created: ${file}`);const keep=Math.max(1,Number(process.env.BACKUP_RETENTION||14));const files=fs.readdirSync(dir).filter(x=>/^eduquinn-.*\.sql\.gz$/.test(x)).sort().reverse();for(const old of files.slice(keep))try{fs.unlinkSync(path.join(dir,old))}catch{}console.log(`Retention: newest ${keep} backup(s).`)})
