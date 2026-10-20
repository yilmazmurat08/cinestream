package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android'in düzenli ifade motoru (ICU), bilgisayardaki Java motorunun kabul ettiği kaçışsız "]" ve "}"
 * karakterlerini reddeder; uygulama telefonda açılırken çöker (Spor ekranında yaşandı). Birim testleri Java
 * motoruyla çalıştığı için bu hatayı göremez; bu test kaynak kodu tarayıp o kalıbı yakalar.
 */
class AndroidRegexSafetyTest {

    private val literal = Regex("Regex\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    @Test
    fun noUnescapedClosingBracketsInRegexLiterals() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val sources = listOf("app/src/main", "sports/src/main").map { File(root, it) }.filter { it.exists() }
        val bad = sources.flatMap { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                literal.findAll(file.readText()).map { it.groupValues[1] }
                    // "[^\]]*]" ya da "[^}]*}": karakter sınıfından hemen sonra kaçışsız kapanış.
                    .filter { it.contains("]*]") || it.contains("]*}") || it.contains("]+]") || it.contains("]+}") }
                    .map { "${file.name}: $it" }
            }
        }
        assertTrue("Android'de çöken düzenli ifadeler:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
