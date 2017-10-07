package eu.mikroskeem.asynclightning.interfaces;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.EnumSkyBlock;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @author Mark Vainomaa
 */
public interface AsyncLightingWorldServer {
    boolean checkLightFor(EnumSkyBlock lightType, BlockPosition pos);
    boolean checkLightAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk chunk, List<Chunk> neighbors);
    boolean updateLightAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk currentChunk);
    ExecutorService getLightingExecutor();
}