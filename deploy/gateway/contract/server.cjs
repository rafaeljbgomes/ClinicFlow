const http=require('node:http');
const counts=new Map(), servers=new Map();
function listen(port) {
  const server=http.createServer(async(req,res)=>{
    const chunks=[];for await(const chunk of req) chunks.push(chunk);
    const key=`${port}:${req.method}:${req.url}`;
    counts.set(key,(counts.get(key)||0)+1);
    const url=new URL(req.url,'http://fixture');
    if(url.searchParams.has('delay')) await new Promise(r=>setTimeout(r,Number(url.searchParams.get('delay'))));
    const status=Number(url.searchParams.get('status')||200);
    res.writeHead(status,{'content-type':'application/json','x-correlation-id':req.headers['x-correlation-id']||'fixture-correlation'});
    res.end(JSON.stringify({port,method:req.method,url:req.url,body:Buffer.concat(chunks).toString(),authorization:req.headers.authorization,correlation:req.headers['x-correlation-id'],forwarded:req.headers['x-forwarded-for'],count:counts.get(key)}));
  }).listen(port,'0.0.0.0');
  servers.set(port,server);
}
for(const port of [3000,8082,8083,8084,8085]) listen(port);
http.createServer((req,res)=>{
  const u=new URL(req.url,'http://fixture'),port=Number(u.searchParams.get('port'));
  if(u.pathname==='/stop') servers.get(port)?.close();
  if(u.pathname==='/start') listen(port);
  res.end('ok');
}).listen(9090,'0.0.0.0');
