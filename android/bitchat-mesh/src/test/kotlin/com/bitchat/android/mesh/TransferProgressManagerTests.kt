package com.bitchat.android.mesh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferProgressManagerTests {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emitsProgressEvents() = runBlocking {
        val channel = Channel<TransferProgressEvent>(Channel.BUFFERED)
        val job = launch {
            TransferProgressManager.events.take(3).collect { event ->
                channel.trySend(event)
            }
        }

        yield()

        TransferProgressManager.start("t1", 10)
        TransferProgressManager.progress("t1", 5, 10)
        TransferProgressManager.complete("t1", 10)

        val events = withTimeout(2000) {
            listOf(channel.receive(), channel.receive(), channel.receive())
        }

        job.cancel()

        assertEquals(0, events[0].sent)
        assertEquals(5, events[1].sent)
        assertEquals(10, events[2].sent)
        assertEquals(true, events[2].completed)
    }
}
