package org.embulk.output.multi;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.spi.OutputPlugin;
import org.embulk.util.config.TaskMapper;
import org.embulk.util.config.ConfigMapperFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class AsyncRunControl {
    private static final String THREAD_NAME_FORMAT = "multi-run-control-%d";
    private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY = ConfigMapperFactory.builder().addDefaultModules().build();
    
    private final MultiOutputPlugin.PluginTask task;
    private final OutputPlugin.Control control;
    private final CountDownLatch latch;
    private final ConcurrentMap<String, TaskSource> taskSources;
    private final ExecutorService executorService;
    private final Future<List<TaskReport>> result;

    static AsyncRunControl start(MultiOutputPlugin.PluginTask task, OutputPlugin.Control control) {
        return new AsyncRunControl(task, control);
    }

    private AsyncRunControl(MultiOutputPlugin.PluginTask task, OutputPlugin.Control control) {
        this.task = task;
        this.control = control;
        this.latch = new CountDownLatch(task.getOutputConfigs().size());
        this.taskSources = new ConcurrentHashMap<>(task.getOutputConfigs().size());
        this.executorService = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat(THREAD_NAME_FORMAT).build()
        );
        this.result = executorService.submit(new RunControl());
    }

    void cancel() {
        result.cancel(true);
    }

    void addTaskSource(String tag, TaskSource taskSource) {
        taskSources.putIfAbsent(tag, taskSource);
        latch.countDown();
    }

    List<TaskReport> waitAndGetResult() throws ExecutionException, InterruptedException {
        try {
            return result.get();
        } finally {
            executorService.shutdown();
        }
    }

    private class RunControl implements Callable<List<TaskReport>> {
        @Override
        public List<TaskReport> call() throws Exception {
            latch.await();
            task.setTaskSources(taskSources);
            return control.run(task.dump());
        }
    }
}
