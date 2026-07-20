package com.percivalll.fastjson.jsontype;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Component
public class WorkerParser implements DisposableBean {
    private final FastjsonHarness harness;
    private final ExecutorService executor;

    public WorkerParser(FastjsonHarness harness) {
        this.harness = harness;
        final ClassLoader appClassLoader = WorkerParser.class.getClassLoader();
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "fatjar-worker");
                thread.setContextClassLoader(appClassLoader);
                return thread;
            }
        });
    }

    public CompletableFuture<Map<String, Object>> parse(final String payload) {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<Map<String, Object>>() {
            @Override
            public Map<String, Object> get() {
                return harness.parse("worker-thread", payload);
            }
        }, executor);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
