"""Public APK v2/v3 signing-block transport. Private signing keys never enter GitHub.
Local: sign BUILD_DIR PATCH_JSON (QUSWIS_SIGNING_STORE/PASSWORD and APKSIGNER env).
CI: apply BUILD_DIR PATCH_JSON OUT_DIR. Only the signing block/ZIP offset may change.
"""
import base64,hashlib,json,os,struct,subprocess,sys
from pathlib import Path

def sha(data):return hashlib.sha256(data).hexdigest()
def positions(data):
 e=data.rfind(b'PK\x05\x06',max(0,len(data)-65557))
 if e<0 or e+22+struct.unpack_from('<H',data,e+20)[0]!=len(data):raise ValueError('Invalid ZIP end record')
 return e,struct.unpack_from('<I',data,e+16)[0]
def extract(before,after):
 e,cd=positions(before);se,scd=positions(after);block=after[cd:scd]
 if before[:cd]!=after[:cd] or before[cd:e]!=after[scd:se]:raise ValueError('Signer changed APK entries; v1 must be disabled')
 old=bytearray(before[e:]);new=bytearray(after[se:]);struct.pack_into('<I',new,16,cd)
 if old!=new or len(block)<32 or block[-16:]!=b'APK Sig Block 42':raise ValueError('Unexpected signature-only change')
 return {'unsigned_sha256':sha(before),'signed_sha256':sha(after),'central_directory':cd,'block':base64.b64encode(block).decode()}
def apply(data,patch):
 if sha(data)!=patch['unsigned_sha256']:raise ValueError('Wrong build artifact')
 e,cd=positions(data)
 if cd!=patch['central_directory']:raise ValueError('Wrong ZIP directory')
 block=base64.b64decode(patch['block'],validate=True)
 if block[-16:]!=b'APK Sig Block 42':raise ValueError('Invalid signing block')
 out=bytearray(data[:cd]+block+data[cd:]);struct.pack_into('<I',out,e+len(block)+16,cd+len(block))
 if sha(out)!=patch['signed_sha256']:raise ValueError('Signed APK digest differs')
 return out
if __name__=='__main__':
 mode,folder,record=sys.argv[1:4];root=Path(folder);path=Path(record)
 if mode=='sign':
  meta=json.loads((root/'build-info.json').read_text());result={'build':meta,'files':{}}
  for name in ['launcher.apk','instrumentation.apk','update-fixture.apk']:
   signed=root/(name+'.signed')
   command=[os.environ['APKSIGNER'],'sign','--ks',os.environ['QUSWIS_SIGNING_STORE'],'--ks-key-alias','quswis','--ks-pass','env:QUSWIS_SIGNING_PASSWORD','--key-pass','env:QUSWIS_SIGNING_PASSWORD','--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',str(signed),str(root/name)]
   subprocess.run(command,check=True,stdout=subprocess.DEVNULL)
   result['files'][name]=extract((root/name).read_bytes(),signed.read_bytes())
   subprocess.run([os.environ['APKSIGNER'],'verify',str(signed)],check=True)
  path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(result,indent=2)+'\n');print('Public signature blocks written; private key retained locally.')
 elif mode=='apply':
  target=Path(sys.argv[4]);target.mkdir(parents=True,exist_ok=True);record=json.loads(path.read_text())
  for name,patch in record['files'].items():(target/name).write_bytes(apply((root/name).read_bytes(),patch))
  print('All reconstructed APK hashes match locally signed APKs.')
 else:raise SystemExit('Unknown operation')
