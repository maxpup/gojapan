package com.gojapan.feature.personal

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class PersonalEngineTest {
    private class Storage : PersonalStorage {
        var data = PersonalSnapshot()
        var failSave = false
        override fun read() = data
        override fun write(snapshot: PersonalSnapshot) {
            if (failSave && snapshot.entries != data.entries) throw java.io.IOException("disk full")
            data = snapshot
        }
    }
    private val content = AnalysisContent("トイレはどこですか", "といれはどこですか", "厕所在哪里？", "问路", "礼貌",
        listOf(AnalysisToken(0, 3, "トイレ", "といれ", "名词", "トイレ", "厕所"), AnalysisToken(3, 4, "は", "わ", "助词", "は", "主题"), AnalysisToken(4, 6, "どこ", "どこ", "疑问词", "どこ", "哪里"), AnalysisToken(6, 9, "ですか", "ですか", "助动词", "です", "疑问")),
        listOf(AnalysisGrammar(3, 4, "は", "主题标记", "主题", null, null, "标记询问主题", null), AnalysisGrammar(4, 6, "どこ", "谓语", null, "位置疑问", null, "询问位置", "駅はどこですか"), AnalysisGrammar(6, 9, "ですか", "句尾", "疑问", "ですか", "礼貌体", "礼貌疑问", null)), emptyList())
    private inner class Client : PersonalAnalysisClient {
        override val provider = "fake"
        override val model = "fixture-1"
        var calls = 0
        var response = personalJson.encodeToString(content)
        var failure: Exception? = null
        override suspend fun analyze(request: String): String { calls++; failure?.let { throw it }; return response }
    }
    @Test fun savesReusesAndSurvivesRestart() = runBlocking {
        val storage = Storage(); val client = Client(); val engine = PersonalEngine(storage, client)
        val id = engine.generate(content.japanese, "日语")
        assertEquals(id, engine.generate(content.japanese, "日语"))
        assertEquals(1, client.calls)
        val reopened = PersonalEngine(storage, Client().apply { failure = java.io.IOException() })
        reopened.initialize()
        assertEquals(content, reopened.state.value.entries.single().versions.single().content)
        assertEquals("待核实", reopened.state.value.entries.single().versions.single().verification)
        assertEquals(6, reopened.state.value.traces.first().steps.size)
    }
    @Test fun protectsManualVersionAndAllowsExplicitSelection() = runBlocking {
        val storage = Storage(); val engine = PersonalEngine(storage, Client())
        val id = engine.generate(content.japanese, "日语")
        engine.change(id, "人工纠正", 1, content.copy(chinese = "洗手间在哪里？"))
        engine.generate(content.japanese, "日语", id)
        val entry = engine.state.value.entries.single()
        assertEquals(3, entry.versions.size); assertEquals(2, entry.current)
        assertEquals("洗手间在哪里？", entry.versions[1].content.chinese)
        engine.change(id, "选择版本", 1)
        assertEquals(1, engine.state.value.entries.single().current)
    }
    @Test fun invalidResponseAndWriteFailureNeverPublishContent() = runBlocking {
        val storage = Storage(); val client = Client(); val engine = PersonalEngine(storage, client)
        client.response = "not json"
        assertTrue(runCatching { engine.generate("我想要一杯热咖啡", "中文/待判断") }.isFailure)
        assertTrue(storage.data.entries.isEmpty())
        val firstTrace = storage.data.traces.single().id
        client.response = personalJson.encodeToString(content); storage.failSave = true
        assertTrue(runCatching { engine.generate("我想要一杯热咖啡", "中文/待判断", retryOf = firstTrace) }.isFailure)
        assertTrue(storage.data.entries.isEmpty())
        assertEquals("失败", storage.data.traces.last().steps.last().status)
        assertEquals("本地写入", storage.data.traces.last().steps.last().name)
        storage.failSave = false
        engine.generate("我想要一杯热咖啡", "中文/待判断")
        assertEquals(1, storage.data.entries.size)
    }
    @Test fun failedUpdateLeavesCurrentVersionAndLogsRetry() = runBlocking {
        val storage = Storage(); val client = Client(); val engine = PersonalEngine(storage, client)
        val id = engine.generate(content.japanese, "日语")
        client.failure = com.gojapan.core.ai.AiChatException("AI 请求超时，请重试")
        assertTrue(runCatching { engine.generate(content.japanese, "日语", id) }.isFailure)
        assertEquals(1, storage.data.entries.single().versions.size)
        assertEquals("失败", storage.data.traces.last().steps.last().status)
        assertEquals("AI 请求", storage.data.traces.last().steps.last().name)
    }
    @Test fun recoversInterruptedTraceAndRedactsExportAndClearsLogs() = runBlocking {
        val storage = Storage()
        storage.data = PersonalSnapshot(traces = listOf(ProcessingTrace("interrupted", steps = listOf(ProcessingStep("AI 请求", 1)))))
        val engine = PersonalEngine(storage, Client(), { "secret123" })
        engine.initialize()
        assertEquals("失败", storage.data.traces.single().steps.single().status)
        engine.generate("secret123 sk-abcdefgh Bearer token123", "中文/待判断")
        val exported = engine.export()
        assertFalse(exported.contains("secret123")); assertFalse(exported.contains("sk-abcdefgh")); assertFalse(exported.contains("token123"))
        engine.clearLogs()
        assertTrue(storage.data.traces.isEmpty()); assertEquals(1, storage.data.entries.size)
    }
    @Test fun chineseIntentSavesOriginalAndScene() = runBlocking {
        val storage = Storage()
        val coffee = AnalysisContent("温かいコーヒーを一杯ください", "あたたかいこーひーをいっぱいください", "请给我一杯热咖啡", "咖啡店点单", "礼貌",
            listOf(AnalysisToken(0, 3, "温かい", "あたたかい", "形容词", "温かい", "热的"), AnalysisToken(3, 7, "コーヒー", "こーひー", "名词", "コーヒー", "咖啡"), AnalysisToken(7, 8, "を", "を", "助词", "を", "宾语"), AnalysisToken(8, 10, "一杯", "いっぱい", "数量词", "一杯", "一杯"), AnalysisToken(10, 14, "ください", "ください", "动词", "くださる", "请给")),
            listOf(AnalysisGrammar(7, 8, "を", "宾语标记", "标记宾语", null, null, "指出请求的物品", null)), emptyList())
        val engine = PersonalEngine(storage, Client().apply { response = personalJson.encodeToString(coffee) })
        engine.generate("我想要一杯热咖啡", "中文/待判断")
        assertEquals("我想要一杯热咖啡", storage.data.entries.single().input)
        assertEquals(coffee, storage.data.entries.single().versions.single().content)
    }
    @Test fun emptyAndLongInputAreLoggedWithoutCallingAi() = runBlocking {
        val storage = Storage(); val client = Client(); val engine = PersonalEngine(storage, client)
        assertTrue(runCatching { engine.generate("", "日语") }.isFailure)
        assertTrue(runCatching { engine.generate("あ".repeat(1001), "日语") }.isFailure)
        assertEquals(0, client.calls)
        assertEquals(2, storage.data.traces.size)
        assertTrue(storage.data.traces.all { it.steps.last().status == "失败" })
    }
    @Test fun correctionsReanchorAndRejectMissingFragments() {
        val shifted = reanchorContent(content.copy(japanese = "あ" + content.japanese))
        assertEquals(1, shifted.tokens.first().start)
        assertTrue(runCatching { reanchorContent(content.copy(japanese = "別の文")) }.isFailure)
    }
    @Test fun validatesSpansAndOriginalAndMissingFields() {
        assertTrue(validateContent(content, content.japanese).isEmpty())
        assertFalse(validateContent(content, "別の文").isEmpty())
        assertFalse(validateContent(content.copy(tokens = listOf(content.tokens.first().copy(end = 999)))).isEmpty())
        assertFalse(validateContent(content.copy(tokens = listOf(content.tokens.first().copy(reading = null)))).isEmpty())
    }
}
