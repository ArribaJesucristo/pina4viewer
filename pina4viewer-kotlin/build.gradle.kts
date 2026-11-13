import java.io.File

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

// Gradle 8.5 + JDK 19 on Windows passes @argfile encoded in UTF-8 while java.exe reads @argfile
// with ACP (Cp1252), breaking non-ASCII workspace paths (e.g. Piña4Viewer) in TestWorker.
subprojects {
    tasks.withType<Test>().configureEach {
        doFirst {
            if (!System.getProperty("os.name").lowercase().contains("win")) return@doFirst
            val rootPath = rootProject.projectDir.absolutePath
            if (rootPath.none { it.code > 127 }) return@doFirst

            var targetDir: File? = rootProject.projectDir
            while (targetDir != null && (targetDir.parentFile?.absolutePath?.any { it.code > 127 } == true)) {
                targetDir = targetDir.parentFile
            }
            val nonAsciiAncestor = targetDir ?: return@doFirst
            val shortAncestor = runCatching {
                val proc = ProcessBuilder("cmd", "/c", "for %I in (\"${nonAsciiAncestor.absolutePath}\") do @echo %~sI")
                    .redirectErrorStream(true)
                    .start()
                val out = proc.inputStream.bufferedReader().readText().trim()
                if (proc.waitFor() == 0 && out.isNotEmpty() && out.none { it.code > 127 } && File(out).exists()) out else null
            }.getOrNull() ?: return@doFirst

            val prefix = nonAsciiAncestor.absolutePath
            val mapped = classpath.files.map { file ->
                val path = file.absolutePath
                if (path.startsWith(prefix, ignoreCase = true)) {
                    val candidate = File(shortAncestor + path.substring(prefix.length))
                    if (candidate.exists()) candidate else file
                } else {
                    file
                }
            }
            classpath = project.files(mapped)
        }
    }
}

