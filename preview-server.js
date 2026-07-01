const http = require('http')
const fs = require('fs')
const path = require('path')

const DIST = path.join(__dirname, 'beichen-erp-web', 'dist')
const PORT = 8000

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json'
}

const server = http.createServer((req, res) => {
  const url = req.url

  // 禁止缓存 + CORS（确保每次都拿到最新代码）
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate')
  res.setHeader('Pragma', 'no-cache')
  res.setHeader('Expires', '0')
  res.setHeader('Access-Control-Allow-Origin', '*')

  // 1. API 请求代理到后端 8080
  if (url.startsWith('/api/')) {
    const proxyReq = http.request(
      {
        hostname: '127.0.0.1',
        port: 8080,
        path: url,
        method: req.method,
        headers: { ...req.headers, host: '127.0.0.1:8080' }
      },
      (proxyRes) => {
        res.writeHead(proxyRes.statusCode || 200, proxyRes.headers)
        proxyRes.pipe(res)
      }
    )
    proxyReq.on('error', () => {
      res.writeHead(502, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ code: 502, msg: '后端服务不可用' }))
    })
    req.pipe(proxyReq)
    return
  }

  // 2. 静态文件服务
  let filePath = path.join(DIST, url.split('?')[0])
  if (url === '/' || url === '') filePath = path.join(DIST, 'index.html')

  fs.readFile(filePath, (err, data) => {
    if (err) {
      // 3. SPA fallback：找不到的路径返回 index.html
      fs.readFile(path.join(DIST, 'index.html'), (e2, html) => {
        if (e2) {
          res.writeHead(404)
          res.end('Not Found')
          return
        }
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
        res.end(html)
      })
      return
    }
    const ext = path.extname(filePath)
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' })
    res.end(data)
  })
})

server.listen(PORT, '0.0.0.0', () => {
  console.log(`预览服务已启动: http://0.0.0.0:${PORT}`)
})
