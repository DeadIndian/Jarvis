package com.jarvis.llm

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFirstLLMRouterTest {
    @Test
    fun usesLocalProviderWhenItSucceeds() = runTest {
        val local = FakeProvider(name = "local", output = "local answer")
        val cloud = FakeProvider(name = "cloud", output = "cloud answer")
        val router = LocalFirstLLMRouter(localProvider = local, cloudProvider = cloud)

        val response = router.complete(LLMRequest(prompt = "hello", allowCloudFallback = true))

        assertEquals("local", response.provider)
        assertEquals("local answer", response.text)
        assertEquals(1, local.calls)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun fallsBackToCloudWhenLocalFailsAndFallbackAllowed() = runTest {
        val local = FakeProvider(name = "local", error = IllegalStateException("offline"))
        val cloud = FakeProvider(name = "cloud", output = "cloud answer")
        val router = LocalFirstLLMRouter(localProvider = local, cloudProvider = cloud)

        val response = router.complete(LLMRequest(prompt = "hello", allowCloudFallback = true))

        assertEquals("cloud", response.provider)
        assertEquals("cloud answer", response.text)
        assertEquals(1, local.calls)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun returnsUnavailableWhenLocalFailsAndFallbackDisabled() = runTest {
        val local = FakeProvider(name = "local", error = IllegalStateException("offline"))
        val cloud = FakeProvider(name = "cloud", output = "cloud answer")
        val router = LocalFirstLLMRouter(localProvider = local, cloudProvider = cloud)

        val response = router.complete(LLMRequest(prompt = "hello", allowCloudFallback = false))

        assertEquals("none", response.provider)
        assertEquals("I could not complete that request right now", response.text)
        assertEquals(1, local.calls)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun usesCloudWhenNoLocalAndFallbackAllowed() = runTest {
        val cloud = FakeProvider(name = "cloud", output = "cloud answer")
        val router = LocalFirstLLMRouter(localProvider = null, cloudProvider = cloud)

        val response = router.complete(LLMRequest(prompt = "hello", allowCloudFallback = true))

        assertEquals("cloud", response.provider)
        assertEquals("cloud answer", response.text)
        assertEquals(1, cloud.calls)
    }

    private class FakeProvider(
        override val name: String,
        private val output: String? = null,
        private val error: Throwable? = null
    ) : LLMProvider {
        var calls: Int = 0

        override suspend fun complete(prompt: String): String {
            calls += 1
            error?.let { throw it }
            return output.orEmpty()
        }
    }
}