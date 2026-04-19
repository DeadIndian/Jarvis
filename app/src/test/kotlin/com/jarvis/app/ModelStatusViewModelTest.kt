package com.jarvis.app

import com.jarvis.logging.JarvisLogger
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeModelManagerTest {
    @Test
    fun fakeModelManagerListsModels() {
        val manager = FakeModelManager()
        val models = runBlocking { manager.listModels() }
        
        assertEquals(2, models.size)
        assertEquals("model1", models[0].id)
        assertEquals("model2", models[1].id)
    }

    @Test
    fun fakeModelManagerTracksActiveModel() {
        val manager = FakeModelManager()
        
        runBlocking {
            val initial = manager.getActiveModelId()
            assertEquals("model1", initial)
            
            manager.setActiveModel("model2")
            val updated = manager.getActiveModelId()
            assertEquals("model2", updated)
        }
    }

    @Test
    fun fakeModelManagerFiltersInstalledModels() {
        val manager = FakeModelManager()
        
        runBlocking {
            val installed = manager.installedModels()
            val available = installed.filter { it.status == OnDeviceModelStatus.AVAILABLE }
            
            assertEquals(1, available.size)
            assertEquals("model1", available[0].id)
        }
    }
}

class ModelStatusUiStateTest {
    @Test
    fun initialStateHasNoDownload() {
        val state = ModelStatusUiState()
        
        assertNull(state.downloadingModelId)
        assertEquals(0, state.downloadProgress)
        assertNull(state.errorMessage)
    }

    @Test
    fun copyPreservesUnchangedFields() {
        val original = ModelStatusUiState(
            models = listOf(),
            activeModelId = "model1",
            errorMessage = null
        )
        
        val updated = original.copy(
            downloadProgress = 50
        )
        
        assertEquals("model1", updated.activeModelId)
        assertEquals(50, updated.downloadProgress)
    }

    @Test
    fun downloadingStateShowsProgress() {
        val state = ModelStatusUiState(
            downloadingModelId = "model1",
            downloadProgress = 75
        )
        
        assertEquals("model1", state.downloadingModelId)
        assertEquals(75, state.downloadProgress)
    }
}

class FakeModelManager : OnDeviceModelManager {
    private var activeModel = "model1"

    override suspend fun listModels(): List<OnDeviceModel> {
        return listOf(
            OnDeviceModel(
                id = "model1",
                title = "Test Model 1",
                source = OnDeviceModelSource.LITERT,
                status = OnDeviceModelStatus.AVAILABLE
            ),
            OnDeviceModel(
                id = "model2",
                title = "Test Model 2",
                source = OnDeviceModelSource.LITERT,
                status = OnDeviceModelStatus.DOWNLOADABLE
            )
        )
    }

    override suspend fun getActiveModelId(): String = activeModel

    override suspend fun setActiveModel(modelId: String) {
        val supported = listModels().map { it.id }
        require(supported.contains(modelId)) { "Unsupported model" }
        activeModel = modelId
    }

    override suspend fun downloadModel(modelId: String, onProgress: (Int) -> Unit) {
        onProgress(100)
        // Simulate download
    }

    override suspend fun deleteModel(modelId: String) {
        // Simulate delete
    }

    override suspend fun complete(prompt: String): String {
        return "Test response"
    }

    override suspend fun installedModels(): List<OnDeviceModel> {
        return listModels().filter { it.status == OnDeviceModelStatus.AVAILABLE }
    }
}

class FakeJarvisLogger : JarvisLogger {
    override fun info(stage: String, message: String, data: Map<String, Any?>) {}
    override fun error(stage: String, message: String, throwable: Throwable?, data: Map<String, Any?>) {}
    override fun warn(stage: String, message: String, data: Map<String, Any?>) {}
}
