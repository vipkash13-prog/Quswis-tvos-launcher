"""Remote-only Android installer approval in an isolated CI emulator. Never uninstalls or clears data."""
import subprocess,time,re,xml.etree.ElementTree as ET
from pathlib import Path
P='com.techgenius.tvoslauncher';out=Path('evidence/installer-start.log')
def adb(*args,timeout=35):return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=timeout)
adb('shell','appops','set',P,'REQUEST_INSTALL_PACKAGES','allow')
with out.open('w') as log:
 process=subprocess.Popen(['adb','shell','am','instrument','-w','-r','-e','profile','true','-e','features','true','-e','installer','true',P+'.test/'+P+'.LiveTvInstrumentation'],stdout=log,stderr=subprocess.STDOUT)
 deadline=time.time()+140
 while time.time()<deadline:
  if 'INSTALLER_USER_CONFIRMATION_REQUESTED' in out.read_text():break
  if process.poll() is not None:raise RuntimeError('Installer setup failed: inspect installer-start.log')
  time.sleep(1)
 else:raise RuntimeError('Installer setup timed out')
 pressed=False
 for _ in range(20):
  try:
   adb('shell','uiautomator','dump','/sdcard/quswis-installer.xml',timeout=35)
   xml=adb('shell','cat','/sdcard/quswis-installer.xml');root=ET.fromstring(xml[xml.index('<?xml'):])
   targets=[n for n in root.iter('node') if n.get('text','').strip().lower() in {'update','install','تحديث','تثبيت'} and n.get('clickable')=='true']
   if targets:
    target=targets[0]
    if target.get('focused')=='true':adb('shell','input','keyevent','23');pressed=True;break
    focused=[n for n in root.iter('node') if n.get('focused')=='true']
    def x(n):
     a=list(map(int,re.findall(r'\d+',n.get('bounds',''))));return(a[0]+a[2])/2 if len(a)==4 else 0
    key='22' if not focused or x(target)>x(focused[0]) else '21'
    adb('shell','input','keyevent',key)
   else:adb('shell','input','keyevent','20')
  except (subprocess.TimeoutExpired,ValueError,ET.ParseError):pass
  time.sleep(1)
 if not pressed:raise RuntimeError('Could not focus and approve Android Update/Install button with D-pad')
 deadline=time.time()+120
 while time.time()<deadline:
  info=adb('shell','dumpsys','package',P)
  if re.search(r'versionCode=208\b',info):break
  time.sleep(2)
 else:raise RuntimeError('Confirmed update did not reach versionCode 208')
 try:process.wait(timeout=10)
 except subprocess.TimeoutExpired:process.terminate()
result=adb('shell','am','instrument','-w','-r','-e','profile','true','-e','features','true','-e','retained','true',P+'.test/'+P+'.LiveTvInstrumentation',timeout=180)
Path('evidence/update-retained.log').write_text(result)
assert 'UPDATE_DATA_RETAINED_OK' in result and 'FEATURES_NATIVE_OK' in result
print('UPDATE_INSTALLER_DPAD_OK: Android approval, same signer, higher code, preferences/favorites/Keystore data retained.')
