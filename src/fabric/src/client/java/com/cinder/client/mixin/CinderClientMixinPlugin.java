package com.cinder.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies client mixin compatibility policy before Mixin transforms classes.
 *
 * <p>Sodium is Cinder's required terrain-renderer foundation. The active
 * client mixin list contains only the Sodium terrain hook and atlas/resource
 * hooks; this plugin keeps the Sodium hook crash-safe if the dependency setup
 * is broken in a development environment.
 *
 * <h2>Threading</h2>
 *
 * <p>Mixin calls this during client startup on the launch thread.
 *
 * <h2>Performance</h2>
 *
 * <p>Startup-only policy lookup. No render-path cost.
 */
public final class CinderClientMixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> SODIUM_TERRAIN_MIXINS = Set.of(
            "com.cinder.client.mixin.SodiumBlockRendererCtmMixin");

    private boolean sodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName,
                                    String mixinClassName) {
        if (SODIUM_TERRAIN_MIXINS.contains(mixinClassName)) {
            return sodiumLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets,
                              Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName,
                         ClassNode targetClass,
                         String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName,
                          ClassNode targetClass,
                          String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
