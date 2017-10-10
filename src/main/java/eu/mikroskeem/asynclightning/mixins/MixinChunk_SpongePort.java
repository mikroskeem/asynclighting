package eu.mikroskeem.asynclightning.mixins;

import eu.mikroskeem.asynclightning.interfaces.AsyncLightingChunk;
import net.minecraft.server.v1_12_R1.BaseBlockPosition;
import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.MCUtil;
import net.minecraft.server.v1_12_R1.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mark Vainomaa
 */
@Mixin(value = Chunk.class, remap = false, priority = 1001)
public abstract class MixinChunk_SpongePort implements AsyncLightingChunk {
    @Shadow @Final public World world;
    @Shadow @Final public int locX;
    @Shadow @Final public int locZ;
    private final Chunk[] chunkNeighbors = new Chunk[4];

    // MCP - onLoad -> addEntities
    @Inject(method = "addEntities", at = @At("RETURN"))
    private void onLoad(CallbackInfo ci) {
        //AsyncLighting.INSTANCE.logger.debug("Populating chunk {} neighbors", this);

        /* Iterate over neighbors */
        for(EnumDirection direction: EnumDirection.EnumDirectionLimit.HORIZONTAL.a()) {
            BaseBlockPosition pos = new BaseBlockPosition(this.locX + direction.getAdjacentX(), 0, this.locZ + direction.getAdjacentZ());
            //AsyncLighting.INSTANCE.logger.debug("Direction {}, pos: {}", direction, pos);

            Chunk neighborChunk = MCUtil.getLoadedChunkWithoutMarkingActive(this.world, pos.getX(), pos.getZ());
            if(neighborChunk == null)
                continue;

            //AsyncLighting.INSTANCE.logger.debug("Adding chunk {} as {} neighbor", neighborChunk, direction);

            int neighborIndex = directionToIndex(direction);
            int oppositeIndex = directionToIndex(direction.opposite());
            this.setNeighbor(neighborIndex, neighborChunk);
            ((AsyncLightingChunk) neighborChunk).setNeighbor(oppositeIndex, (Chunk) (Object) this);
        }
    }

    // MCP - onUnload -> removeEntities
    @Inject(method = "removeEntities", at = @At("RETURN"))
    private void onUnload(CallbackInfo ci) {
        //AsyncLighting.INSTANCE.logger.debug("Clearing chunk {} neighbors", this);

        /* Iterate over neighbors */
        for(EnumDirection direction: EnumDirection.EnumDirectionLimit.HORIZONTAL.a()) {
            BaseBlockPosition pos = new BaseBlockPosition(this.locX + direction.getAdjacentX(), 0, this.locZ + direction.getAdjacentZ());

            Chunk neighborChunk = MCUtil.getLoadedChunkWithoutMarkingActive(this.world, pos.getX(), pos.getZ());
            if(neighborChunk == null)
                continue;

            int neighborIndex = directionToIndex(direction);
            int oppositeIndex = directionToIndex(direction.opposite());
            this.setNeighbor(neighborIndex, null);
            ((AsyncLightingChunk) neighborChunk).setNeighbor(oppositeIndex, null);
        }
    }


    @Override
    public boolean areNeighborsLoaded() {
        for(int i = 0; i < 4; i++) {
            if(this.chunkNeighbors[i] == null)
                return false;
        }
        return true;
    }

    @Override
    public Chunk getNeighborChunk(int index) {
        return this.chunkNeighbors[index];
    }

    @Override
    public List<Chunk> getNeighbors() {
        List<Chunk> neighborList = new ArrayList<>();
        for (Chunk neighbor : this.chunkNeighbors) {
            if (neighbor != null) {
                neighborList.add(neighbor);
            }
        }
        return neighborList;
    }

    @Override
    public void setNeighbor(int index, Chunk neighborChunk) {
        this.chunkNeighbors[index] = neighborChunk;
    }

    private static int directionToIndex(EnumDirection direction) {
        switch (direction) {
            case NORTH:
                return 0;
            case SOUTH:
                return 1;
            case EAST:
                return 2;
            case WEST:
                return 3;
            default:
                throw new IllegalArgumentException("Unexpected direction");
        }
    }
}
