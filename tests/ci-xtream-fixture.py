"""Test-only live API. Uses a real public NHK HLS stream and an actual captured TS segment.
No subscription credentials, recordings or channel data are shipped in the launcher.
"""
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from urllib.parse import urlparse,parse_qs
from pathlib import Path
import json,threading,sys
ROOT=Path(sys.argv[1]);LOCK=threading.Lock();COUNTS={}
HLS='https://masterpl.hls.nhkworld.jp/hls/w/live/smarttv.m3u8'
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_GET(self):
  p=urlparse(self.path);q=parse_qs(p.query)
  if p.path=='/player_api.php':
   action=q.get('action',['authenticate'])[0]
   with LOCK:
    COUNTS[action]=COUNTS.get(action,0)+1;(ROOT/'api-audit.json').write_text(json.dumps(COUNTS))
   if q.get('username')!=['ci_native_user'] or q.get('password')!=['ci_native_secret_207']:self.reply({'user_info':{'auth':0}});return
   if action=='authenticate':self.reply({'user_info':{'auth':1,'status':'Active','allowed_output_formats':['m3u8','ts']}})
   elif action=='get_live_categories':self.reply([{'category_id':'1','category_name':'قنوات عامة مباشرة'},{'category_id':'2','category_name':'Live transport test'}])
   elif action=='get_live_streams':self.reply([{'stream_id':1,'name':'NHK World Japan','stream_type':'live','category_id':'1','stream_icon':'http://10.0.2.2:8765/logo.png'},{'stream_id':2,'name':'إن إتش كيه — اختبار النقل','stream_type':'live','category_id':'2','stream_icon':'http://10.0.2.2:8765/logo.png'}])
   else:self.send_error(400)
  elif p.path.startswith('/live/ci_native_user/ci_native_secret_207/'):
   if p.path.endswith('.m3u8'):
    if p.path.endswith('/2.m3u8'):self.send_error(503);return
    self.send_response(302);self.send_header('Location',HLS);self.end_headers()
   elif p.path.endswith('.ts'):self.file(ROOT/'live.ts','video/mp2t')
   else:self.send_error(404)
  elif p.path=='/logo.png':self.file(ROOT/'logo.png','image/png')
  else:self.send_error(404)
 def reply(self,data):
  encoded=json.dumps(data,ensure_ascii=False).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(encoded)));self.end_headers();self.wfile.write(encoded)
 def file(self,path,mime):
  if not path.is_file():self.send_error(404);return
  self.send_response(200);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(path.stat().st_size));self.end_headers()
  try:
   with path.open('rb') as f:
    while True:
     chunk=f.read(32768)
     if not chunk:break
     self.wfile.write(chunk)
  except (BrokenPipeError,ConnectionResetError):pass
ThreadingHTTPServer(('0.0.0.0',8765),Handler).serve_forever()
