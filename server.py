import http.server
import urllib.request
import urllib.error
import os

PORT = 8000
API_HOST = "http://localhost:8080"
DIST_DIR = "/workspace/beichen-erp-web/dist"

class ProxyHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIST_DIR, **kwargs)

    def do_GET(self):
        if self.path.startswith('/api/'):
            self._proxy('GET')
        elif self.path == '/' or not os.path.exists(DIST_DIR + self.path):
            self.path = '/index.html'
            super().do_GET()
        else:
            super().do_GET()

    def do_POST(self):
        if self.path.startswith('/api/'):
            self._proxy('POST')
        else:
            self.send_response(404)
            self.end_headers()

    def do_PUT(self):
        if self.path.startswith('/api/'):
            self._proxy('PUT')
        else:
            self.send_response(404)
            self.end_headers()

    def do_DELETE(self):
        if self.path.startswith('/api/'):
            self._proxy('DELETE')
        else:
            self.send_response(404)
            self.end_headers()

    def _proxy(self, method):
        url = API_HOST + self.path
        body = None
        content_length = int(self.headers.get('Content-Length', 0))
        if content_length > 0:
            body = self.rfile.read(content_length)
        req = urllib.request.Request(url, data=body, method=method)
        for key, val in self.headers.items():
            if key.lower() not in ('host', 'content-length'):
                req.add_header(key, val)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                self.send_response(resp.status)
                for key, val in resp.headers.items():
                    if key.lower() not in ('transfer-encoding', 'connection'):
                        self.send_header(key, val)
                self.end_headers()
                self.wfile.write(resp.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            self.send_response(502)
            self.end_headers()
            self.wfile.write(str(e).encode())

if __name__ == '__main__':
    httpd = http.server.HTTPServer(('0.0.0.0', PORT), ProxyHandler)
    print(f"Serving on port {PORT}, proxying /api/* to {API_HOST}")
    httpd.serve_forever()
