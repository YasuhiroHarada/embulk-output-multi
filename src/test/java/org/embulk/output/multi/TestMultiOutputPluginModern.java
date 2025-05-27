package org.embulk.output.multi;

import org.embulk.config.ConfigSource;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.type.Types;
import org.embulk.util.config.ConfigMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMultiOutputPluginModern {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestMultiOutputPluginModern.class);
    
    private MultiOutputPlugin plugin;
    private Schema schema;
    
    @BeforeEach
    void setUp() {
        plugin = new MultiOutputPlugin();
        
        // テスト用のスキーマを定義
        schema = Schema.builder()
                .add("id", Types.LONG)
                .add("name", Types.STRING)
                .add("age", Types.LONG)
                .build();
    }
    
    @Test
    void testBasicConfiguration() {
        LOGGER.info("Testing basic configuration parsing");
        
        ConfigMapperFactory configMapperFactory = ConfigMapperFactory.builder().addDefaultModules().build();
        
        // 基本的な設定をテスト
        ConfigSource config = configMapperFactory.newConfigSource()
                .set("outputs", Arrays.asList(
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout"),
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                ));
        
        // 設定の妥当性をテスト
        MultiOutputPlugin.PluginTask task = configMapperFactory.createConfigMapper()
                .map(config, MultiOutputPlugin.PluginTask.class);
        
        assertNotNull(task);
        assertEquals(2, task.getOutputConfigs().size());
        assertEquals("stdout", task.getOutputConfigs().get(0).get(String.class, "type"));
        assertEquals("stdout", task.getOutputConfigs().get(1).get(String.class, "type"));
        
        LOGGER.info("Basic configuration test passed");
    }
    
    @Test
    void testEmptyOutputsConfiguration() {
        LOGGER.info("Testing empty outputs configuration");
        
        ConfigMapperFactory configMapperFactory = ConfigMapperFactory.builder().addDefaultModules().build();
        
        // 空の出力設定をテスト
        ConfigSource config = configMapperFactory.newConfigSource()
                .set("outputs", Arrays.asList());
        
        // プラグインタスクのコンフィグレーション
        MultiOutputPlugin.PluginTask task = configMapperFactory.createConfigMapper()
                .map(config, MultiOutputPlugin.PluginTask.class);
        
        // トランザクション実行時にエラーになることを確認
        assertThrows(Exception.class, () -> {
            plugin.transaction(config, schema, 1, new OutputPlugin.Control() {
                @Override
                public List<org.embulk.config.TaskReport> run(org.embulk.config.TaskSource taskSource) {
                    return Arrays.asList();
                }
            });
        });
        
        LOGGER.info("Empty outputs configuration test passed");
    }
    
    @Test
    void testConfigurationWithValidOutputs() {
        LOGGER.info("Testing configuration with valid outputs");
        
        ConfigMapperFactory configMapperFactory = ConfigMapperFactory.builder().addDefaultModules().build();
        
        // 有効な出力設定をテスト
        ConfigSource config = configMapperFactory.newConfigSource()
                .set("outputs", Arrays.asList(
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                                .set("prints_column_names", true),
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                                .set("prints_column_names", false)
                ));
        
        MultiOutputPlugin.PluginTask task = configMapperFactory.createConfigMapper()
                .map(config, MultiOutputPlugin.PluginTask.class);
        
        assertNotNull(task);
        assertEquals(2, task.getOutputConfigs().size());
        
        // 各出力設定の詳細をチェック
        ConfigSource firstOutput = task.getOutputConfigs().get(0);
        assertEquals("stdout", firstOutput.get(String.class, "type"));
        assertTrue(firstOutput.get(Boolean.class, "prints_column_names"));
        
        ConfigSource secondOutput = task.getOutputConfigs().get(1);
        assertEquals("stdout", secondOutput.get(String.class, "type"));
        assertFalse(secondOutput.get(Boolean.class, "prints_column_names"));
        
        LOGGER.info("Valid outputs configuration test passed");
    }
    
    @Test
    void testPluginInstantiation() {
        LOGGER.info("Testing plugin instantiation");
        
        // プラグインが正常にインスタンス化できることを確認
        assertNotNull(plugin);
        assertInstanceOf(OutputPlugin.class, plugin);
        
        LOGGER.info("Plugin instantiation test passed");
    }
    
    @Test
    void testSchemaHandling() {
        LOGGER.info("Testing schema handling");
        
        // スキーマが正しく設定されていることを確認
        assertNotNull(schema);
        assertEquals(3, schema.getColumnCount());
        assertEquals("id", schema.getColumn(0).getName());
        assertEquals("name", schema.getColumn(1).getName());
        assertEquals("age", schema.getColumn(2).getName());
        assertEquals(Types.LONG, schema.getColumn(0).getType());
        assertEquals(Types.STRING, schema.getColumn(1).getType());
        assertEquals(Types.LONG, schema.getColumn(2).getType());
        
        LOGGER.info("Schema handling test passed");
    }
    
    @Test
    void testPluginLoadingMechanism() {
        LOGGER.info("Testing plugin loading mechanism");
        
        ConfigMapperFactory configMapperFactory = ConfigMapperFactory.builder().addDefaultModules().build();
        
        // stdout プラグインを使用した設定
        ConfigSource config = configMapperFactory.newConfigSource()
                .set("outputs", Arrays.asList(
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                                .set("prints_column_names", true)
                ));
        
        // プラグインが設定を正しく処理できることを確認
        assertDoesNotThrow(() -> {
            MultiOutputPlugin.PluginTask task = configMapperFactory.createConfigMapper()
                    .map(config, MultiOutputPlugin.PluginTask.class);
            
            assertNotNull(task);
            assertEquals(1, task.getOutputConfigs().size());
            assertEquals("stdout", task.getOutputConfigs().get(0).get(String.class, "type"));
        });
        
        LOGGER.info("Plugin loading mechanism test passed");
    }
    
    @Test
    void testComplexOutputConfiguration() {
        LOGGER.info("Testing complex output configuration");
        
        ConfigMapperFactory configMapperFactory = ConfigMapperFactory.builder().addDefaultModules().build();
        
        // 複数の異なるタイプの出力設定
        ConfigSource config = configMapperFactory.newConfigSource()
                .set("outputs", Arrays.asList(
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                                .set("prints_column_names", true),
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                                .set("prints_column_names", false),
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                                .set("prints_column_names", true)
                ));
        
        MultiOutputPlugin.PluginTask task = configMapperFactory.createConfigMapper()
                .map(config, MultiOutputPlugin.PluginTask.class);
        
        assertNotNull(task);
        assertEquals(3, task.getOutputConfigs().size());
        
        // 各出力の設定を検証
        assertTrue(task.getOutputConfigs().get(0).get(Boolean.class, "prints_column_names"));
        assertFalse(task.getOutputConfigs().get(1).get(Boolean.class, "prints_column_names"));
        assertTrue(task.getOutputConfigs().get(2).get(Boolean.class, "prints_column_names"));
        
        LOGGER.info("Complex output configuration test passed");
    }
    
    @Test
    void testConfigurationDefaults() {
        LOGGER.info("Testing configuration defaults");
        
        ConfigMapperFactory configMapperFactory = ConfigMapperFactory.builder().addDefaultModules().build();
        
        // 最小限の設定
        ConfigSource config = configMapperFactory.newConfigSource()
                .set("outputs", Arrays.asList(
                        configMapperFactory.newConfigSource()
                                .set("type", "stdout")
                ));
        
        MultiOutputPlugin.PluginTask task = configMapperFactory.createConfigMapper()
                .map(config, MultiOutputPlugin.PluginTask.class);
        
        assertNotNull(task);
        assertEquals(1, task.getOutputConfigs().size());
        
        // オプショナルフィールドのデフォルト値をテスト
        assertFalse(task.getOutputConfigDiffs().isPresent());
        assertNull(task.getTaskSources());
        
        LOGGER.info("Configuration defaults test passed");
    }
}
