package org.embulk.output.multi;

import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;
import org.embulk.plugin.DefaultPluginType;
import org.embulk.spi.Exec;
import org.embulk.spi.ExecSession;
import org.embulk.spi.ExecSessionInternal;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.TransactionalPageOutput;
import org.embulk.util.config.Config;
import org.embulk.util.config.ConfigDefault;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;
import org.embulk.util.config.Task;
import org.embulk.util.config.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class MultiOutputPlugin implements OutputPlugin {
    public interface PluginTask extends Task {
        @Config("outputs")
        List<ConfigSource> getOutputConfigs();

        @Config(CONFIG_NAME_OUTPUT_CONFIG_DIFFS)
        @ConfigDefault("null")
        Optional<Map<String, ConfigDiff>> getOutputConfigDiffs();

        Map<String, TaskSource> getTaskSources();
        void setTaskSources(Map<String, TaskSource> taskSources);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiOutputPlugin.class);
    private static final String CONFIG_NAME_OUTPUT_CONFIG_DIFFS = "output_config_diffs";
    static final String CONFIG_NAME_OUTPUT_TASK_REPORTS = "output_task_reports";
    
    private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY = ConfigMapperFactory.builder().addDefaultModules().build();

    @Override
    public ConfigDiff transaction(ConfigSource config, Schema schema, int taskCount, OutputPlugin.Control control) {
        final ConfigMapper configMapper = CONFIG_MAPPER_FACTORY.createConfigMapper();
        final PluginTask task = configMapper.map(config, PluginTask.class);
        if (task.getOutputConfigs().isEmpty()) {
            throw new ConfigException("'outputs' must have more than or equals to 1 element.");
        }
        final ExecSession session = Exec.session();
        final AsyncRunControl runControl = AsyncRunControl.start(task, control);
        return buildConfigDiff(mapWithPluginDelegate(task, session, delegate ->
                delegate.transaction(schema, taskCount, runControl)
        ));
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource, Schema schema, int taskCount, OutputPlugin.Control control) {
        final TaskMapper taskMapper = CONFIG_MAPPER_FACTORY.createTaskMapper();
        final PluginTask task = taskMapper.map(taskSource, PluginTask.class);
        final ExecSession session = Exec.session();
        final AsyncRunControl runControl = AsyncRunControl.start(task, control);
        return buildConfigDiff(mapWithPluginDelegate(task, session, delegate ->
                delegate.resume(schema, taskCount, runControl)
        ));
    }

    @Override
    public void cleanup(TaskSource taskSource, Schema schema, int taskCount, List<org.embulk.config.TaskReport> successTaskReports) {
        final TaskMapper taskMapper = CONFIG_MAPPER_FACTORY.createTaskMapper();
        final PluginTask task = taskMapper.map(taskSource, PluginTask.class);
        final ExecSession session = Exec.session();
        mapWithPluginDelegate(task, session, delegate -> {
            delegate.cleanup(schema, taskCount, successTaskReports);
            return null;
        });
    }

    @Override
    public TransactionalPageOutput open(TaskSource taskSource, Schema schema, int taskIndex) {
        final TaskMapper taskMapper = CONFIG_MAPPER_FACTORY.createTaskMapper();
        final PluginTask task = taskMapper.map(taskSource, PluginTask.class);
        final ExecSession session = Exec.session();
        return MultiTransactionalPageOutput.open(schema, taskIndex, mapWithPluginDelegate(task, session, Function.identity()));
    }

    private static ConfigDiff buildConfigDiff(List<OutputPluginDelegate.Transaction> transactions) {
        final ConfigDiff configDiff = Exec.newConfigDiff();
        Map<String, ConfigDiff> configDiffs = new HashMap<>();
        for (OutputPluginDelegate.Transaction transaction: transactions) {
            try {
                configDiffs.put(transaction.getTag(), transaction.getResult());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        configDiff.set(CONFIG_NAME_OUTPUT_CONFIG_DIFFS, configDiffs);
        return configDiff;
    }    private <T> List<T> mapWithPluginDelegate(PluginTask task, ExecSession session, Function<OutputPluginDelegate, T> action) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < task.getOutputConfigs().size(); i++) {
            final ConfigSource config = task.getOutputConfigs().get(i);
            final String pluginTypeName = config.get(String.class, "type");
            
            // Embulk 0.10.43での正しいプラグインローディング方法
            final OutputPlugin outputPlugin;
            try {
                // ExecSessionInternalを使用してプラグインを取得
                final ExecSessionInternal sessionInternal = (ExecSessionInternal) session;
                outputPlugin = sessionInternal.newPlugin(OutputPlugin.class, 
                    DefaultPluginType.create(pluginTypeName));
                LOGGER.debug("Successfully loaded output plugin: {}", pluginTypeName);
            } catch (Exception e) {
                LOGGER.error("Failed to load output plugin: " + pluginTypeName, e);
                throw new RuntimeException("Plugin loading failed for: " + pluginTypeName, e);
            }

            final String tag = String.format("%s_%d", pluginTypeName, i);

            // Merge ConfigDiff if exists
            if (task.getOutputConfigDiffs().isPresent()) {
                final ConfigDiff configDiff = task.getOutputConfigDiffs().get().get(tag);
                if (configDiff != null) {
                    config.merge(configDiff);
                } else {
                    LOGGER.debug("ConfigDiff for '{}' not found.", tag);
                }
            }
            // Set TaskSource if exists
            TaskSource taskSource = null;
            if (task.getTaskSources() != null) {
                taskSource = task.getTaskSources().get(tag);
            }

            result.add(action.apply(new OutputPluginDelegate(tag, outputPlugin, config, taskSource)));
        }
        return result;
    }
}
