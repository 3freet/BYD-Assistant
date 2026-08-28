package com.kangrio.byd.assistant.standalone.stt

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private class FakeSttEngine(private val result: SttResult, val onCalled: () -> Unit = {}) : SttEngine {
    var wasCalled = false
        private set

    override suspend fun transcribe(languageTag: String?): SttResult {
        wasCalled = true
        onCalled()
        return result
    }
}

class FallbackSttEngineTest {

    @Test
    fun `falls through to fallback only when primary is NOT_AVAILABLE`() {
        val primary = FakeSttEngine(SttResult.Failure(SttError.NOT_AVAILABLE))
        val fallback = FakeSttEngine(SttResult.Success("hello"))

        val result = runBlocking { FallbackSttEngine(primary, fallback).transcribe("en") }

        assertTrue(primary.wasCalled)
        assertTrue(fallback.wasCalled)
        assertEquals(SttResult.Success("hello"), result)
    }

    @Test
    fun `does not call fallback when primary succeeds`() {
        val primary = FakeSttEngine(SttResult.Success("hi"))
        val fallback = FakeSttEngine(SttResult.Success("should not be used"))

        val result = runBlocking { FallbackSttEngine(primary, fallback).transcribe("en") }

        assertTrue(primary.wasCalled)
        assertFalse(fallback.wasCalled)
        assertEquals(SttResult.Success("hi"), result)
    }

    @Test
    fun `does not call fallback for a different failure reason`() {
        val primary = FakeSttEngine(SttResult.Failure(SttError.NO_SPEECH_DETECTED))
        val fallback = FakeSttEngine(SttResult.Success("should not be used"))

        val result = runBlocking { FallbackSttEngine(primary, fallback).transcribe("en") }

        assertTrue(primary.wasCalled)
        assertFalse(fallback.wasCalled)
        assertEquals(SttResult.Failure(SttError.NO_SPEECH_DETECTED), result)
    }

    @Test
    fun `returns primary's failure untouched when no fallback is supplied`() {
        val primary = FakeSttEngine(SttResult.Failure(SttError.NOT_AVAILABLE))

        val result = runBlocking { FallbackSttEngine(primary, fallback = null).transcribe("en") }

        assertEquals(SttResult.Failure(SttError.NOT_AVAILABLE), result)
    }
}
