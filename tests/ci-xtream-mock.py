#!/usr/bin/env python3
"""Tiny Xtream-style server for CI. Logs every request so the workflow can prove VOD/series are never requested."""
import json, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

ROOT = sys.argv[1]
LOG = sys.argv[2]
USER, PASS = "test", "secret"

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _log(self):
        with open(LOG, "a") as f: f.write(self.path.split("password=")[0] + ("password=***" if "password=" in self.path else "") + "\n")
    def _send(self, code, body, ctype):
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        self._log()
        u = urlparse(self.path); q = parse_qs(u.query)
        if u.path == "/player_api.php":
            if q.get("username") != [USER] or q.get("password") != [PASS]:
                return self._send(401, b"{}", "application/json")
            action = (q.get("action") or [""])[0]
            if action == "get_live_categories":
                body = [{"category_id": "1", "category_name": "اختبار HLS"}, {"category_id": "2", "category_name": "اختبار TS"}]
            elif action == "get_live_streams":
                body = [{"num": 1, "name": "HLS Test", "stream_type": "live", "stream_id": 1, "stream_icon": "", "category_id": "1", "container_extension": "m3u8"},
                        {"num": 2, "name": "TS Test", "stream_type": "live", "stream_id": 2, "stream_icon": "", "category_id": "2", "container_extension": "ts"}]
            elif action == "":
                body = {"user_info": {"auth": 1, "status": "Active", "allowed_output_formats": ["m3u8", "ts"]}, "server_info": {}}
            else:
                return self._send(404, b"{}", "application/json")
            return self._send(200, json.dumps(body).encode(), "application/json")
        parts = u.path.strip("/").split("/")
        if len(parts) == 4 and parts[0] == "live" and parts[1] == USER and parts[2] == PASS:
            name = parts[3]
            path = {"1.m3u8": "hls/out.m3u8", "2.ts": "test.ts"}.get(name, "hls/" + name)
            full = os.path.join(ROOT, path)
            if os.path.isfile(full):
                ctype = "application/vnd.apple.mpegurl" if full.endswith(".m3u8") else "video/mp2t"
                return self._send(200, open(full, "rb").read(), ctype)
        self._send(404, b"not found", "text/plain")

ThreadingHTTPServer(("127.0.0.1", 8099), H).serve_forever()
