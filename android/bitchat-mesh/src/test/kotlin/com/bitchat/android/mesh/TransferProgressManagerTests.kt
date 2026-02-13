package com.bitchat.android.mesh

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransferProgressManagerTests {
    @Test
    fun emitsProgressEvents() = runBlocking {
        val channel = Channel<TransferProgressEvent>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            TransferProgressManager.events.take(3).collect { event ->
                channel.send(event)
            }
        }

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
