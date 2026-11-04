from pathlib import Path
import xml.etree.ElementTree as ET
original=ET.parse('AndroidManifest.xml').getroot()
merged=ET.parse('live-tv-build/build/generated/live/AndroidManifest.xml').getroot()
assert original.attrib.pop('package')=='com.techgenius.tvoslauncher'
key='{http://schemas.android.com/apk/res/android}usesCleartextTraffic'
assert merged.find('application').attrib.pop(key)=='true'
assert ET.tostring(original)==ET.tostring(merged), 'Original components, HOME or Accessibility changed'
print('LIVE_MANIFEST_OK: original components unchanged; HTTP public streams enabled only in generated build manifest')
