/*
 * This file is part of project asynclighting, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered (original implementation)
 * Copyright (c) 2017 Mark Vainomaa <mikroskeem@mikroskeem.eu>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
    @Inject public Logger logger;
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
