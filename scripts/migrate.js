require('../db').migrate().then(ok=>{console.log(ok?'Migration complete':'DATABASE_URL not configured');process.exit(ok?0:1)}).catch(e=>{console.error(e);process.exit(1)});
