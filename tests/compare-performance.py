from pathlib import Path
import json,sys
root=Path(sys.argv[1]);out=['# Measured performance comparison','', 'One Android 14 emulator, same input script, animations enabled, network disabled during profiling. Values are measurements, not physical MECOOL results. Frame p95 includes GPU/emulator work; main CPU is cumulative CPU during the stage. Repeated window opens include cold and warm paths.','']
for size in ['1920x1080','3840x2160']:
    before=json.loads((root/f'profile-baseline-{size}.json').read_text())
    after=json.loads((root/f'profile-candidate-{size}.json').read_text())
    out += [f'## {size}','','| Stage | Before main CPU ms | After main CPU ms | Before frame p95 ms | After frame p95 ms | Before/after PSS MiB |','|---|---:|---:|---:|---:|---:|']
    candidates={s['stage']:s for s in after['stages']}
    for b in before['stages']:
        a=candidates[b['stage']]
        out.append(f"| {b['stage']} | {b['main_cpu_ms']:.1f} | {a['main_cpu_ms']:.1f} | {b['frame_p95_ms']:.1f} | {a['frame_p95_ms']:.1f} | {b['pss_kib']/1024:.1f} / {a['pss_kib']/1024:.1f} |")
    out+=['','Raw JSON contains frame counts, frames over 50ms, disk-read/write violations, heap sizes and sampled main-thread stacks. PSS also includes graphics/native/service allocations and is not an isolated Java heap figure.','']
(root/'performance-comparison.md').write_text('\n'.join(out)+'\n')
print('\n'.join(out))
