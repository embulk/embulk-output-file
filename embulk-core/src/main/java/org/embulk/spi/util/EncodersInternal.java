package org.embulk.spi.util;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;
import org.embulk.plugin.PluginType;
import org.embulk.spi.EncoderPlugin;
import org.embulk.spi.ExecSessionInternal;
import org.embulk.spi.FileOutput;

public abstract class EncodersInternal {
    private EncodersInternal() {}

    public static List<EncoderPlugin> newEncoderPlugins(ExecSessionInternal exec, List<ConfigSource> configs) {
        ImmutableList.Builder<EncoderPlugin> builder = ImmutableList.builder();
        for (ConfigSource config : configs) {
            builder.add(exec.newPlugin(EncoderPlugin.class, config.get(PluginType.class, "type")));
        }
        return builder.build();
    }

    public interface Control {
        public void run(List<TaskSource> taskSources);
    }

    public static void transaction(List<EncoderPlugin> plugins, List<ConfigSource> configs,
            EncodersInternal.Control control) {
        new RecursiveControl(plugins, configs, control).transaction();
    }

    public static FileOutput open(List<EncoderPlugin> plugins, List<TaskSource> taskSources,
            FileOutput output) {
        FileOutput out = output;
        int pos = 0;
        while (pos < plugins.size()) {
            out = plugins.get(pos).open(taskSources.get(pos), out);
            pos++;
        }
        return out;
    }

    private static class RecursiveControl {
        private final List<EncoderPlugin> plugins;
        private final List<ConfigSource> configs;
        private final EncodersInternal.Control finalControl;
        private final ImmutableList.Builder<TaskSource> taskSources;
        private int pos;

        RecursiveControl(List<EncoderPlugin> plugins, List<ConfigSource> configs,
                EncodersInternal.Control finalControl) {
            this.plugins = plugins;
            this.configs = configs;
            this.finalControl = finalControl;
            this.taskSources = ImmutableList.builder();
        }

        public void transaction() {
            if (pos < plugins.size()) {
                plugins.get(pos).transaction(configs.get(pos), new EncoderPlugin.Control() {
                        public void run(TaskSource taskSource) {
                            taskSources.add(taskSource);
                            pos++;
                            transaction();
                        }
                    });
            } else {
                finalControl.run(taskSources.build());
            }
        }
    }
}
