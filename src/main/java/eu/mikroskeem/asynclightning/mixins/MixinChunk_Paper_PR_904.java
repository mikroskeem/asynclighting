package eu.mikroskeem.asynclightning.mixins;

import net.minecraft.server.v1_12_R1.Chunk;
import org.spigotmc.SpigotWorldConfig;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * See https://github.com/PaperMC/Paper/pull/904/commits/f4147b5a552302970e098942ad6912b462f97816
 *
 * @author Mark Vainomaa
 */
@Mixin(value = Chunk.class, remap = false)
public abstract class MixinChunk_Paper_PR_904 {
    private final static String ASYNC$SpigotWorldConfig_randomLightUpdates = "Lorg/spigotmc/SpigotWorldConfig;randomLightUpdates:Z";

    @Shadow private boolean r;
    @Shadow private boolean done;
    @Shadow private boolean lit;

    @Redirect(method = "b(Z)V", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = ASYNC$SpigotWorldConfig_randomLightUpdates
    ))
    private boolean getRandomLightUpdates(SpigotWorldConfig spigotWorldConfig) {
        return true;
    }

    /* Rewrite whole method */
    public boolean isReady() {
        return this.r && this.done && this.lit;
    }
}
