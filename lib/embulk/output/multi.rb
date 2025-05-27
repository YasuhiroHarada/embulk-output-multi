require 'embulk/java/plugin'
Embulk::JavaPlugin.register_plugin(
  "output", "multi", "org.embulk.output.multi.MultiOutputPlugin")
