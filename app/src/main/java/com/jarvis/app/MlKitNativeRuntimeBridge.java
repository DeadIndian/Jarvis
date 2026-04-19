package com.jarvis.app;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ModelConfig;
import com.google.mlkit.genai.prompt.ModelPreference;
import com.google.mlkit.genai.prompt.ModelReleaseStage;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

final class MlKitNativeRuntimeBridge {
    static final int STATUS_UNKNOWN = -1;
    static final int STATUS_UNAVAILABLE = FeatureStatus.UNAVAILABLE;
    static final int STATUS_DOWNLOADABLE = FeatureStatus.DOWNLOADABLE;
    static final int STATUS_DOWNLOADING = FeatureStatus.DOWNLOADING;
    static final int STATUS_AVAILABLE = FeatureStatus.AVAILABLE;

    private static final String MODEL_FAST_STABLE = "mlkit-fast-stable";
    private static final String MODEL_FULL_STABLE = "mlkit-full-stable";

    private static final class ModelSpec {
        final String id;
        final String title;
        final int preference;
        final int releaseStage;

        ModelSpec(String id, String title, int preference, int releaseStage) {
            this.id = id;
            this.title = title;
            this.preference = preference;
            this.releaseStage = releaseStage;
        }
    }

    private final Map<String, ModelSpec> specsById;
    private final Map<String, GenerativeModelFutures> clients;

    MlKitNativeRuntimeBridge() {
        Map<String, ModelSpec> specs = new LinkedHashMap<>();
        specs.put(
                MODEL_FAST_STABLE,
                new ModelSpec(MODEL_FAST_STABLE, "ML Kit Fast (Stable)", ModelPreference.FAST,
                        ModelReleaseStage.STABLE));
        specs.put(
                MODEL_FULL_STABLE,
                new ModelSpec(MODEL_FULL_STABLE, "ML Kit Full (Stable)", ModelPreference.FULL,
                        ModelReleaseStage.STABLE));
        this.specsById = Collections.unmodifiableMap(specs);
        this.clients = new LinkedHashMap<>();
    }

    List<String> getSupportedModelIds() {
        return new ArrayList<>(specsById.keySet());
    }

    String getModelTitle(String modelId) {
        return requireSpec(modelId).title;
    }

    int checkStatus(String modelId) {
        try {
            Integer raw = getClient(modelId).checkStatus().get();
            return raw == null ? STATUS_UNKNOWN : raw;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MLKit status check interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("MLKit status check failed", e.getCause());
        }
    }

    void downloadModel(String modelId) {
        int status = checkStatus(modelId);
        if (status == STATUS_AVAILABLE) {
            return;
        }

        if (status == STATUS_DOWNLOADABLE || status == STATUS_DOWNLOADING) {
            awaitDownload(getClient(modelId).download(new NoOpDownloadCallback()));
            return;
        }

        throw new IllegalStateException("Native MLKit model is unavailable on this device");
    }

    void deleteModel(String modelId) {
        try {
            getClient(modelId).clearImplicitCaches().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MLKit cache clear interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("MLKit cache clear failed", e.getCause());
        }
    }

    String complete(String modelId, String prompt) {
        ensureModelReady(modelId);
        GenerativeModelFutures model = getClient(modelId);

        try {
            GenerateContentResponse response = model.generateContent(prompt).get();
            return firstCandidateText(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MLKit generation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("MLKit generation failed", e.getCause());
        }
    }

    private void ensureModelReady(String modelId) {
        int status = checkStatus(modelId);
        if (status == STATUS_AVAILABLE) {
            return;
        }
        if (status == STATUS_DOWNLOADABLE || status == STATUS_DOWNLOADING) {
            downloadModel(modelId);
            return;
        }
        throw new IllegalStateException("Native MLKit model unavailable on this device");
    }

    private synchronized GenerativeModelFutures getClient(String modelId) {
        ModelSpec spec = requireSpec(modelId);
        GenerativeModelFutures existing = clients.get(spec.id);
        if (existing != null) {
            return existing;
        }

        ModelConfig.Builder modelConfig = new ModelConfig.Builder();
        modelConfig.setPreference(spec.preference);
        modelConfig.setReleaseStage(spec.releaseStage);

        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        configBuilder.setModelConfig(modelConfig.build());

        GenerativeModel baseModel = Generation.INSTANCE.getClient(configBuilder.build());
        GenerativeModelFutures created = GenerativeModelFutures.from(baseModel);
        clients.put(spec.id, created);
        return created;
    }

    private ModelSpec requireSpec(String modelId) {
        ModelSpec spec = specsById.get(modelId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown model id: " + modelId);
        }
        return spec;
    }

    private void awaitDownload(ListenableFuture<Void> future) {
        try {
            future.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new IllegalStateException("MLKit model download failed", e);
        }
    }

    private String firstCandidateText(GenerateContentResponse response) {
        if (response == null) {
            return "";
        }

        List<Candidate> candidates = response.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        String text = candidates.get(0).getText();
        return text == null ? "" : text;
    }

    private static final class NoOpDownloadCallback implements DownloadCallback {
        @Override
        public void onDownloadStarted(long totalBytes) {
        }

        @Override
        public void onDownloadProgress(long downloadedBytes) {
        }

        @Override
        public void onDownloadCompleted() {
        }

        @Override
        public void onDownloadFailed(GenAiException exception) {
            throw new IllegalStateException(exception == null ? "MLKit download failed" : exception.getMessage(),
                    exception);
        }
    }
}
