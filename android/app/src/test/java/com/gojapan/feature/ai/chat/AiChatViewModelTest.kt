package com.gojapan.feature.ai.chat

import com.gojapan.core.ai.AiChatClient
import com.gojapan.core.ai.AiChatAvailability
import com.gojapan.core.ai.AiApiKeyStore
import com.gojapan.core.ai.AiChatRole
import com.gojapan.core.ai.AiChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendAddsUserAndAssistantMessages() = runTest(dispatcher) {
        val client = RecordingClient("推荐浅草和上野。")
        val viewModel = AiChatViewModel(client, FakeApiKeyStore())

        viewModel.updateInput("东京一天怎么玩？")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.messages.size)
        assertEquals("推荐浅草和上野。", viewModel.state.value.messages.last().text)
        assertEquals(AiChatRole.USER, client.received.single().role)
        assertFalse(viewModel.state.value.isSending)
    }

    @Test
    fun failedSendRestoresInput() = runTest(dispatcher) {
        val viewModel = AiChatViewModel(FailingClient(), FakeApiKeyStore())

        viewModel.updateInput("帮我规划京都")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("帮我规划京都", viewModel.state.value.input)
        assertTrue(viewModel.state.value.errorMessage?.contains("测试失败") == true)
        assertFalse(viewModel.state.value.isSending)
    }

    @Test
    fun unavailableClientIsReportedBeforeSending() = runTest(dispatcher) {
        val viewModel = AiChatViewModel(UnavailableClient(), FakeApiKeyStore())

        assertTrue(viewModel.state.value.availability is AiChatAvailability.Unavailable)
        viewModel.updateInput("不会发送")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.messages.size)
        assertEquals("不会发送", viewModel.state.value.input)
    }

    private class RecordingClient(private val answer: String) : AiChatClient {
        var received: List<AiChatTurn> = emptyList()
        override suspend fun reply(messages: List<AiChatTurn>): String {
            received = messages
            return answer
        }
    }

    private class FailingClient : AiChatClient {
        override suspend fun reply(messages: List<AiChatTurn>): String = error("测试失败")
    }

    private class UnavailableClient : AiChatClient {
        override fun availability() = AiChatAvailability.Unavailable("未配置")
        override suspend fun reply(messages: List<AiChatTurn>): String = error("不应调用")
    }

    private class FakeApiKeyStore : AiApiKeyStore {
        private var value: String? = null
        override fun read(): String? = value
        override fun save(apiKey: String) { value = apiKey }
        override fun clear() { value = null }
    }
}
