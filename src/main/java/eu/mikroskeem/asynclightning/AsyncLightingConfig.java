package eu.mikroskeem.asynclightning;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

/**
 * @author Mark Vainomaa
 */
@ConfigSerializable
public class AsyncLightingConfig {
    @Setting(value = "use-async-lighting", comment = "Whether to enable async lighting or not. On by default")
    public boolean useAsyncLighting = true;

    @Setting(value = "num-threads", comment = "The amount of threads to dedicate for async lighting updates. (Default: 2)")
    public int numAsyncThreads = 2;
}
