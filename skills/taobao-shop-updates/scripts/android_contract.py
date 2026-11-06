"""Compile the actual Android public-package Kotlin source; never use the browser/network."""
import hashlib
import os
import subprocess
import tempfile
from pathlib import Path

RUNNER = '''import com.lolita.app.data.square.SquarePackage
import java.io.File
import java.nio.file.Files
fun main(args: Array<String>) {
    val temp = Files.createTempDirectory("square-contract-").toFile()
    try {
        val c = SquarePackage.unpack(File(args[0]), File(temp, "stage"))
        println("ANDROID_SQUARE_V1_OK shops=${c.shops.size} products=${c.products.size}")
    } finally { temp.deleteRecursively() }
}
'''


def check(package, project):
    project = Path(project).expanduser().resolve()
    source = project / "app/src/main/java/com/lolita/app/data/square/SquarePackage.kt"
    if not source.is_file():
        raise ValueError("找不到App的SquarePackage.kt；请配置正确project，不跳过格式验收")
    modules = Path.home() / ".gradle/caches/modules-2/files-2.1"
    def jar(group, artifact, version=None):
        files = sorted((modules / group / artifact).glob((version or "*") + "/*/*.jar"))
        if not files:
            raise ValueError("缺少本地Kotlin/Gson构建依赖，请先在本工程完成既有构建；不自动下载")
        return files[-1]
    compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.1.0")
    libs = [jar("org.jetbrains.kotlin", a, "2.1.0") for a in ("kotlin-stdlib", "kotlin-script-runtime")]
    gson = jar("com.google.code.gson", "gson")
    dependencies = [compiler, *libs, jar("org.jetbrains.kotlin", "kotlin-reflect"),
                    jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"), jar("org.jetbrains", "annotations")]
    trove = list((modules / "org.jetbrains.intellij.deps/trove4j").glob("*/*/*.jar"))
    dependencies += trove[-1:]
    java_home = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
    java = java_home / "bin/java"
    if not java.is_file():
        raise ValueError("未找到Java运行环境，请设置JAVA_HOME")
    # Temporary compilation avoids stale binaries and does not modify the application source.
    with tempfile.TemporaryDirectory(prefix="lolita-square-kotlin-") as directory:
        root = Path(directory); main = root / "SquareContract.kt"; main.write_text(RUNNER)
        output = root / "contract.jar"
        commands = [
            [str(java), "-cp", os.pathsep.join(map(str, dependencies)),
             "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
             "-classpath", os.pathsep.join(map(str, [*libs, gson])), "-d", str(output), str(source), str(main)],
            [str(java), "-cp", os.pathsep.join(map(str, [output, *libs, gson])), "SquareContractKt", str(Path(package).resolve())],
        ]
        for command in commands:
            result = subprocess.run(command, capture_output=True, text=True, timeout=120)
            if result.returncode:
                # Do not relay paths, source contents, or arbitrary nested exception messages to callers.
                raise ValueError("实际Android Kotlin格式验收失败；保留候选文件核对结构，不推进目录基线")
        if "ANDROID_SQUARE_V1_OK" not in result.stdout:
            raise ValueError("Android格式验收未返回成功标记")
    return {"passed": True, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "method": "compile actual SquarePackage.kt; unpack candidate with bounded SHA/reference checks"}
