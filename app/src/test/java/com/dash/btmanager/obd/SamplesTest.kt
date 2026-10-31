package com.dash.btmanager.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Примеры из samples/ репозитория разбираются тем же парсером, что и
 * встроенные профили (охрана от опечаток в JSON и формулах), а заводские
 * DID сверяются с ответами прямо из obd_log.txt.
 */
class SamplesTest {

    private fun dir(): File =
        sequenceOf(File("samples"), File("../samples"), File("app/samples")).first { it.isDirectory }

    private fun readFile(name: String) = File(dir(), name).readText()

    @Test
    fun samples_parse() {
        val names = dir().listFiles { f -> f.name.endsWith(".json", true) }
            .orEmpty().mapTo(HashSet()) { it.name }
        assertTrue("в samples нет примеров: $names", names.containsAll(
            listOf("chery_tiggo_from_log.json", "standard_obd.json")))
        for (name in names) {
            val p = ObdProfile.parse(readFile(name))
            assertTrue("«$name»: нет параметров", p.params.isNotEmpty())
            assertTrue("«$name»: предупреждений быть не должно: ${p.warnings}", p.warnings.isEmpty())
        }
    }

    @Test
    fun chery_decodesLogReplies() {
        val p = ObdProfile.parse(readFile("chery_tiggo_from_log.json"))
        fun first(cmd: String) = p.params.first { it.command == cmd }
        // Ответы взяты из obd_log.txt (сообщение 17.02 19:16, ATH1-кадры ELM)
        assertEquals(66.0, first("0105").decode("7E80341056A")!!, 0.01)
        assertEquals(-15.0, first("0146").decode("7E803414619")!!, 0.01)
        assertEquals(946.5, first("010C").decode("7E804410C0ECA")!!, 0.01)
        assertEquals(0.996, first("0134").decode("7E8044134 7F 7F 7F F9")!!, 0.001)
        assertEquals(14.206, first("0142").decode("7E8044142377E")!!, 0.001)
        assertEquals(9.0, first("220A5F").decode("71304620A5F09")!!, 1e-9)
        assertEquals(18.5, first("220A7D").decode("71305620A7D0025")!!, 0.01)
        assertEquals(1837.0, first("220A6A").decode("71307620A6A0002CD94")!!, 0.01)
        assertEquals(18.45, first("223003").decode("730056230030148")!!, 0.05)
        assertEquals(215.6, first("223404").decode("750046234049D")!!, 0.1)
        assertEquals(-14.0, first("226802").decode("75B0462680234")!!, 0.01)
    }

    @Test
    fun chery_vin_fromElmMultiframe() {
        val p = ObdProfile.parse(readFile("chery_tiggo_from_log.json"))
        val vin = p.info.first { it.key == "vin" }
        // Много-кадровый ответ ELM на 0902 (стиль ATH0 — нумерованные кадры)
        val reply = """
            014
            0: 49 02 01 45 44 45
            1: 46 42 33 32 42 35 53
            2: 45 30 30 38 35 33 33
        """.trimIndent()
        assertEquals("EDEFB32B5SE008533", vin.decode(reply))
    }
}
