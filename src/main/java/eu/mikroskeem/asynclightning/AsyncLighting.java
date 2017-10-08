package eu.mikroskeem.asynclightning;

import com.google.common.eventbus.Subscribe;
import eu.mikroskeem.orion.api.Orion;
import eu.mikroskeem.orion.api.annotations.OrionMod;
import eu.mikroskeem.orion.api.events.ModConstructEvent;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;


/**
 * See https://github.com/SpongePowered/SpongeCommon/commit/e9414f556af95c5e3c7eff3d0cf67dba6a824cb8
 *
 * @author Mark Vainomaa
 */
@OrionMod(id = "asynclighting")
public final class AsyncLighting {
    @Inject private Logger logger;
    @Inject private Orion orion;
    @Inject private ConfigurationLoader<CommentedConfigurationNode> configurationLoader;

    public static AsyncLighting INSTANCE;
    private CommentedConfigurationNode baseNode;
    private ObjectMapper<AsyncLightingConfig>.BoundInstance om;
    public AsyncLightingConfig config;

    @Subscribe
    public void on(ModConstructEvent e) throws Exception {
        baseNode = configurationLoader.load();
        om = ObjectMapper.forClass(AsyncLightingConfig.class).bindToNew();
        om.populate(baseNode.getNode("asynclighting"));
        om.serialize(baseNode.getNode("asynclighting"));
        configurationLoader.save(baseNode);
        config = om.getInstance();

        logger.info("Sponge Async Lighting V2 patch - Orion port by mikroskeem");
        if(!config.useAsyncLighting) return;

        INSTANCE = this;

        logger.info("Applying Sponge Async Lighting V2 patch");
        orion.registerMixinConfig("mixins.asynclighting.json");
    }
}
