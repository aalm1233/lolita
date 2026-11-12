#!/usr/bin/env python3
"""Build/run exact production API probes under the shared evaluation ledger."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import time
import textwrap
from PIL import Image
import three_view as ev


def build(root):
    repo = Path(__file__).resolve().parents[2]
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    def jar(group, name, version):
        return str(next((cache / group / name / version).glob("*/*.jar")))
    libs = [jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.1.0"),
            jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.1"),
            jar("org.jetbrains", "annotations", "23.0.0"), jar("com.google.code.gson", "gson", "2.11.0")]
    compiler = [jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.1.0"),
                jar("org.jetbrains.kotlin", "kotlin-script-runtime", "2.1.0"),
                jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
                jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"), *libs]
    java = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
    models = repo / "app/build/tmp/kotlin-classes/debug"
    source = repo / "app/src/main/java/com/lolita/app"
    files = [source / f for f in ["data/remote/SeedreamImageApi.kt", "data/remote/ReferenceImageEncoding.kt",
                                 "domain/model/ImageGenerationFailure.kt", "domain/model/StandaloneProductPrompts.kt"]]
    files.append(Path(__file__).with_name("ProductionProbe.kt"))
    out = root / "production-classes"
    result = subprocess.run([java, "-cp", ":".join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                             "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath",
                             ":".join([str(models), *libs]), "-d", str(out), *map(str, files)], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    classes = [models / "com/lolita/app/domain/model" / f for f in
               ["MannequinRequest.class", "MannequinSubject.class", "CategorySemantic.class"]]
    runtime = {"java": java, "classpath": ":".join([str(out), str(models), *libs]),
               "sources": {str(f.relative_to(repo)): ev.sha(f.read_bytes()) for f in files},
               "modelClasses": {str(f.relative_to(repo)): ev.sha(f.read_bytes()) for f in classes},
               "probeClasses": {str(f): ev.sha(f.read_bytes()) for f in out.rglob("*.class")},
               "promptSourceSha256": ev.sha((source / "domain/model/MannequinRequest.kt").read_bytes())}
    ev.save(root / "production-runtime.json", runtime)
    print("Compiled current production transport; existing Debug prompt classes fingerprinted. No HTTP.")


def run(root, jobs, credential):
    runtime = ev.read(root / "production-runtime.json")
    rows = ev.read(jobs)
    previous = {a["id"] for a in ev.ledger(root)["attempts"]}
    if len({j["id"] for j in rows}) != len(rows) or previous.intersection(j["id"] for j in rows):
        raise ValueError("REPEATED_RUN_NO_RETRY")
    if len(previous) + len(rows) > ev.LIMIT:
        raise ValueError("BATCH_EXCEEDS_BUDGET")
    repo = Path(__file__).resolve().parents[2]
    for path, expected in {**runtime["sources"], **runtime["modelClasses"]}.items():
        if ev.sha((repo / path).read_bytes()) != expected:
            raise ValueError("PRODUCTION_RUNTIME_CHANGED_REBUILD_FIRST")
    for path, expected in runtime["probeClasses"].items():
        if ev.sha(Path(path).read_bytes()) != expected:
            raise ValueError("PRODUCTION_PROBE_CHANGED_REBUILD_FIRST")
    for job in rows:
        if not job["id"] or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in job["id"]):
            raise ValueError("UNSAFE_RUN_ID")
        folder = root / "runs" / job["id"]
        folder.mkdir(parents=True, exist_ok=True)
        inputs = [Path(p) for p in job["inputs"]]
        manifest = {**job, "inputSha256": [ev.sha(p.read_bytes()) for p in inputs], "productionRuntime": runtime,
                    "normalization": "Host Pillow, not Android byte equivalence"}
        ev.save(folder / "manifest.json", manifest)
        command = [runtime["java"], "-Djava.awt.headless=true", "-cp", runtime["classpath"], "ProductionProbeKt"]
        subprocess.run([*command, "prepare", str(folder)], check=True, capture_output=True)
        # Standalone policy is freshly compiled from production source above.
        # Dress/blouse still use Debug models, so additionally compare their source literal.
        source = Path(__file__).resolve().parents[2] / "app/src/main/java/com/lolita/app/domain/model/MannequinRequest.kt"
        current = source.read_text()
        if job["presentation"] == "DRESS_FORM":
            key = "BLOUSE_PROMPT" if job["semantic"] == "BLOUSE" else "BASE_PROMPT"
            match = re.search(r'private val ' + key + r' = """(.*?)"""\.trimIndent\(\)', current, re.S)
            expected = textwrap.dedent(match.group(1)).strip()
            if job.get("instructions"):
                expected += "\nAdditional user instruction about the garment: " + job["instructions"]
            if (folder / "prompt.txt").read_text() != expected:
                raise ValueError("DEBUG_PROMPT_DIFFERS_FROM_CURRENT_SOURCE")
        elif job["presentation"] != "ACCESSORY":
            raise ValueError("PROBE_PRESENTATION_NOT_SUPPORTED")
        manifest["promptSha256"] = ev.sha((folder / "prompt.txt").read_bytes())
        ev.save(folder / "manifest.json", manifest)
        if credential.stat().st_mode & 0o077:
            raise ValueError("INSECURE_CREDENTIAL")
        ev.reserve(root, job, manifest["inputSha256"], manifest["promptSha256"])
        started = time.monotonic()
        result = {"id": job["id"], "status": "FAILED", "automaticRetries": 0}
        try:
            process = subprocess.run([*command, "run", str(folder), str(credential)], capture_output=True, text=True, timeout=350)
            result.update(json.loads(process.stdout))
            if result["status"] == "SUCCESS":
                output = folder / "output.png"
                with Image.open(output) as im:
                    im.load()
                    result["size"] = list(im.size)
                result.update(outputSha256=ev.sha(output.read_bytes()), outputCount=1)
        except Exception as e:
            result.update(status="FAILED", failure=type(e).__name__)
        result["seconds"] = round(time.monotonic() - started, 2)
        ev.save(folder / "result.json", result)
        def finish(data):
            next(a for a in data["attempts"] if a["id"] == job["id"]).update(result)
        ev.ledger(root, finish)
        print(json.dumps(result), flush=True)
        if result.get("failure") in ("AUTH", "QUOTA", "RATE_LIMIT", "SEEDREAM_LIMIT"):
            break


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["build", "run"])
    parser.add_argument("root", type=Path)
    parser.add_argument("jobs", type=Path, nargs="?")
    parser.add_argument("--credential", type=Path, default=Path.home() / ".config/lolita/recognition-evaluation.json")
    args = parser.parse_args()
    if args.command == "build": build(args.root)
    else:
        import fcntl
        with (args.root / "batch.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            run(args.root, args.jobs, args.credential)


if __name__ == "__main__":
    main()
