package eu.mikroskeem.asynclightning.mixins;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import eu.mikroskeem.asynclightning.AsyncLighting;
import eu.mikroskeem.asynclightning.interfaces.AsyncLightingChunk;
import eu.mikroskeem.asynclightning.interfaces.AsyncLightingWorldServer;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.EnumSkyBlock;
import net.minecraft.server.v1_12_R1.IChunkProvider;
import net.minecraft.server.v1_12_R1.MCUtil;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Mark Vainomaa
 */
@Mixin(value = WorldServer.class, remap = false)
public abstract class MixinWorldServer implements AsyncLightingWorldServer {
    private static final int NUM_XZ_BITS = 4;
    private static final int NUM_SHORT_Y_BITS = 8;
    private static final short XZ_MASK = 0xF;
    private static final short Y_SHORT_MASK = 0xFF;

    @Shadow @Nullable public abstract MinecraftServer getMinecraftServer();
    @Shadow public abstract IChunkProvider getChunkProvider();

    private ExecutorService lightExecutorService = Executors.newFixedThreadPool(
            AsyncLighting.INSTANCE.config.numAsyncThreads,
            new ThreadFactoryBuilder()
                    .setNameFormat("mikroskeem's AsyncLighting port - Async Light Thread")
                    .build()
    );

    @Override
    public boolean checkLightFor(EnumSkyBlock lightType, BlockPosition pos) {
        return this.updateLightAsync(lightType, pos, null);
    }

    //@Override - remap
    // MCP - checkLightFor
    public boolean c(EnumSkyBlock enumskyblock, BlockPosition blockposition) {
        return checkLightFor(enumskyblock, blockposition);
    }

    @Override
    public boolean updateLightAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk currentChunk) {
        if (this.getMinecraftServer().isStopped() || this.lightExecutorService.isShutdown()) {
            return false;
        }

        if (currentChunk == null) {
            //currentChunk = ((IMixinChunkProviderServer) this.chunkProvider).getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);
            currentChunk = MCUtil.getLoadedChunkWithoutMarkingActive(this.getChunkProvider(), pos.getX() >> 4, pos.getZ() >> 4);
        }

        final AsyncLightingChunk asyncLightingChunk = (AsyncLightingChunk) currentChunk;
        //                          MCP - unloadQueued
        if (currentChunk == null || currentChunk.d || !asyncLightingChunk.areNeighborsLoaded()) {
            return false;
        }

        final short shortPos = this.blockPosToShort(pos);
        if (asyncLightingChunk.getQueuedLightingUpdates(lightType).contains(shortPos)) {
            return false;
        }

        final Chunk chunk = currentChunk;
        asyncLightingChunk.getQueuedLightingUpdates(lightType).add(shortPos);
        asyncLightingChunk.getPendingLightUpdates().incrementAndGet();
        asyncLightingChunk.setLightUpdateTime(chunk.getWorld().getTime());

        List<Chunk> neighbors = asyncLightingChunk.getNeighbors();
        // add diagonal chunks
        Chunk southEastChunk = ((AsyncLightingChunk) asyncLightingChunk.getNeighborChunk(0)).getNeighborChunk(2);
        Chunk southWestChunk = ((AsyncLightingChunk) asyncLightingChunk.getNeighborChunk(0)).getNeighborChunk(3);
        Chunk northEastChunk = ((AsyncLightingChunk) asyncLightingChunk.getNeighborChunk(1)).getNeighborChunk(2);
        Chunk northWestChunk = ((AsyncLightingChunk) asyncLightingChunk.getNeighborChunk(1)).getNeighborChunk(3);
        if (southEastChunk != null) {
            neighbors.add(southEastChunk);
        }
        if (southWestChunk != null) {
            neighbors.add(southWestChunk);
        }
        if (northEastChunk != null) {
            neighbors.add(northEastChunk);
        }
        if (northWestChunk != null) {
            neighbors.add(northWestChunk);
        }

        for(Chunk neighborChunk : neighbors) {
            final AsyncLightingChunk neighbor = (AsyncLightingChunk) neighborChunk;
            neighbor.getPendingLightUpdates().incrementAndGet();
            neighbor.setLightUpdateTime(chunk.getWorld().getTime()); // MCP - getTotalWorldTime()
        }

        if(getMinecraftServer().primaryThread != Thread.currentThread()) {
            this.lightExecutorService.execute(() ->
                this.checkLightAsync(lightType, pos, chunk, neighbors)
            );
        } else {
            this.checkLightAsync(lightType, pos, chunk, neighbors);
        }

        return true;
    }

    @Override
    public boolean checkLightAsync(EnumSkyBlock lightType, BlockPosition pos, Chunk currentChunk, List<Chunk> neighbors) {
        final AsyncLightingChunk asyncLightingChunk = (AsyncLightingChunk) currentChunk;
        int i = 0;
        int j = 0;
        int k = this.getLightForAsync(lightType, pos, currentChunk, neighbors);
        int l = this.getRawBlockLightAsync(lightType, pos, currentChunk, neighbors);
        int i1 = pos.getX();
        int j1 = pos.getY();
        int k1 = pos.getZ();

        if (l > k) {
            this.lightUpdateBlockList[j++] = 133152;
        } else if (l < k) {
            this.lightUpdateBlockList[j++] = 133152 | k << 18;

            while (i < j) {
                int l1 = this.lightUpdateBlockList[i++];
                int i2 = (l1 & 63) - 32 + i1;
                int j2 = (l1 >> 6 & 63) - 32 + j1;
                int k2 = (l1 >> 12 & 63) - 32 + k1;
                int l2 = l1 >> 18 & 15;
                BlockPosition blockpos = new BlockPosition(i2, j2, k2);
                int i3 = this.getLightForAsync(lightType, blockpos, currentChunk, neighbors);

                if (i3 == l2) {
                    this.setLightForAsync(lightType, blockpos, 0, currentChunk, neighbors);

                    if (l2 > 0) {
                        int j3 = MathHelper.a(i2 - i1); // MCP - abs
                        int k3 = MathHelper.a(j2 - j1); // MCP - abs
                        int l3 = MathHelper.a(k2 - k1); // MCP - abs

                        if (j3 + k3 + l3 < 17) {
                            BlockPosition.PooledBlockPosition blockpos$pooledmutableblockpos = BlockPosition.PooledBlockPosition.aquire(); // MCP - retain();

                            // MCP - EnumFacing
                            for(EnumDirection enumfacing : EnumDirection.values()) {
                                int i4 = i2 + enumfacing.getAdjacentX(); // MCP - getFrontOffsetX()
                                int j4 = j2 + enumfacing.getAdjacentY(); // MCP - getFrontOffsetY();
                                int k4 = k2 + enumfacing.getAdjacentZ(); // MCP - getFrontOffsetZ();
                                blockpos$pooledmutableblockpos.setValues(i4, j4, k4); // MCP - setPos(...);
                                final Chunk pooledChunk = this.getLightChunk(blockpos$pooledmutableblockpos, currentChunk, neighbors);
                                if (pooledChunk == null) {
                                    continue;
                                }
                                int l4 = Math.max(1, pooledChunk.getBlockState(blockpos$pooledmutableblockpos).getLightOpacity());
                                i3 = this.getLightForAsync(lightType, blockpos$pooledmutableblockpos, currentChunk, neighbors);

                                if (i3 == l2 - l4 && j < this.lightUpdateBlockList.length) {
                                    this.lightUpdateBlockList[j++] = i4 - i1 + 32 | j4 - j1 + 32 << 6 | k4 - k1 + 32 << 12 | l2 - l4 << 18;
                                }
                            }

                            blockpos$pooledmutableblockpos.free(); // MCP - release
                        }
                    }
                }
            }

            i = 0;
        }

        while (i < j) {
            int i5 = this.lightUpdateBlockList[i++];
            int j5 = (i5 & 63) - 32 + i1;
            int k5 = (i5 >> 6 & 63) - 32 + j1;
            int l5 = (i5 >> 12 & 63) - 32 + k1;
            BlockPosition blockpos1 = new BlockPosition(j5, k5, l5);
            int i6 = this.getLightForAsync(lightType, blockpos1, currentChunk, neighbors); // Sponge - use thread safe method
            int j6 = this.getRawBlockLightAsync(lightType, blockpos1, currentChunk, neighbors); // Sponge - use thread safe method

            if (j6 != i6) {
                this.setLightForAsync(lightType, blockpos1, j6, currentChunk, neighbors); // Sponge - use thread safe method

                if (j6 > i6) {
                    int k6 = Math.abs(j5 - i1);
                    int l6 = Math.abs(k5 - j1);
                    int i7 = Math.abs(l5 - k1);
                    boolean flag = j < this.lightUpdateBlockList.length - 6;

                    if (k6 + l6 + i7 < 17 && flag) {
                        if (this.getLightForAsync(lightType, blockpos1.west(), currentChunk, neighbors) < j6) {
                            this.lightUpdateBlockList[j++] = j5 - 1 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLightForAsync(lightType, blockpos1.east(), currentChunk, neighbors) < j6) {
                            this.lightUpdateBlockList[j++] = j5 + 1 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLightForAsync(lightType, blockpos1.down(), currentChunk, neighbors) < j6) {
                            this.lightUpdateBlockList[j++] = j5 - i1 + 32 + (k5 - 1 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLightForAsync(lightType, blockpos1.up(), currentChunk, neighbors) < j6) {
                            this.lightUpdateBlockList[j++] = j5 - i1 + 32 + (k5 + 1 - j1 + 32 << 6) + (l5 - k1 + 32 << 12);
                        }

                        if (this.getLightForAsync(lightType, blockpos1.north(), currentChunk, neighbors) < j6) {
                            this.lightUpdateBlockList[j++] = j5 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 - 1 - k1 + 32 << 12);
                        }

                        if (this.getLightForAsync(lightType, blockpos1.south(), currentChunk, neighbors) < j6) {
                            this.lightUpdateBlockList[j++] = j5 - i1 + 32 + (k5 - j1 + 32 << 6) + (l5 + 1 - k1 + 32 << 12);
                        }
                    }
                }
            }
        }

        asyncLightingChunk.getQueuedLightingUpdates(lightType).remove((Short) this.blockPosToShort(pos));
        asyncLightingChunk.getPendingLightUpdates().decrementAndGet();
        for (Chunk neighborChunk : neighbors) {
            final AsyncLightingChunk neighbor = (AsyncLightingChunk) neighborChunk;
            neighbor.getPendingLightUpdates().decrementAndGet();
        }

        return true;
    }

    @Override
    public ExecutorService getLightingExecutor() {
        return lightExecutorService;
    }

    private short blockPosToShort(BlockPosition pos) {
        short serialized = (short) setNibble(0, pos.getX() & XZ_MASK, 0, NUM_XZ_BITS);
        serialized = (short) setNibble(serialized, pos.getY() & Y_SHORT_MASK, 1, NUM_SHORT_Y_BITS);
        serialized = (short) setNibble(serialized, pos.getZ() & XZ_MASK, 3, NUM_XZ_BITS);
        return serialized;
    }

    /**
     * Modifies bits in an integer.
     *
     * @param num Integer to modify
     * @param data Bits of data to add
     * @param which Index of nibble to start at
     * @param bitsToReplace The number of bits to replace starting from nibble index
     * @return The modified integer
     */
    private int setNibble(int num, int data, int which, int bitsToReplace) {
        return (num & ~(bitsToReplace << (which * 4)) | (data << (which * 4)));
    }
}
