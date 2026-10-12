"""Pulls the GLSL out of ShaderProgram.kt and runs it past a compiler.

To Kotlin the shaders are two string constants, so a typo in one survives the compiler,
the unit tests and the APK, and turns up as a black table on somebody's phone. This is
the only thing in the project that actually reads them.

Run it from the repository root; needs glslangValidator (apt: glslang-tools).
"""
import pathlib, re, subprocess, sys, tempfile

src = pathlib.Path(sys.argv[1] if len(sys.argv) > 1
                   else "app/src/main/java/com/mrpool/eightball/render/ShaderProgram.kt").read_text()

stages = {"VERTEX": "vert", "FRAGMENT": "frag"}
failed = False
for name, stage in stages.items():
    m = re.search(r'const val %s = """(.*?)"""' % name, src, re.S)
    if not m:
        print(f"could not find {name} in the Kotlin source"); failed = True; continue
    body = m.group(1)
    with tempfile.NamedTemporaryFile("w", suffix="." + stage, delete=False) as f:
        f.write(body)
        path = f.name
    r = subprocess.run(["glslangValidator", path], capture_output=True, text=True)
    ok = r.returncode == 0
    print(f"{name:9s} {'OK' if ok else 'FAILED'}")
    if not ok:
        print(r.stdout.strip()); print(r.stderr.strip()); failed = True

sys.exit(1 if failed else 0)
