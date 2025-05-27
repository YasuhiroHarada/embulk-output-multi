# Multi output plugin for Embulk / Embulk用マルチアウトプットプラグイン

このプロジェクトは kamatama41/embulk-output-multi のフォークです


## Configuration

- **outputs**: Configuration of output plugins (array, required)

## Example

```yaml
out:
  type: multi
  outputs:
    # Output to stdout
    - type: stdout
    # Output to files as CSV
    - type: file
      path_prefix: out_file_
      file_ext: csv
      formatter:
        type: csv
    # Output to files as TSV
    - type: file
      path_prefix: out_file_
      file_ext: tsv
      formatter:
        type: csv
        delimiter: "\t"    # And any outputs you want..
    - type: ...
```

## Quick Start / クイックスタート

### Installation / インストール

1. Build the plugin JAR / プラグインJARをビルド:
```bash
./gradlew build
```

2. Copy the JAR to your Embulk plugins directory / JARをEmbulkプラグインディレクトリにコピー:
```bash
cp build/libs/embulk-output-multi-*.jar $EMBULK_HOME/lib/
```

### Basic Usage / 基本的な使用方法

```yaml
out:
  type: multi
  outputs:
    - type: stdout
    - type: file
      path_prefix: output/data
      file_ext: csv
      formatter:
        type: csv
```


### Build Status / ビルドステータス

### Version 0.5.3-SNAPSHOT (2025-05-27)
- ✅ ** Upgrade**: Successfully migrated from Embulk 0.9.x to 0.10.43 compatibility

  **アップグレード**: Embulk 0.9.xから0.10.43互換性への移行に成功
**Last Updated / 最終更新**: 2025-05-27  
**Embulk Compatibility / Embulk互換性**: 0.9.x (0.9.23+), 0.10.x (0.10.43+)  
