package eu.mikroskeem.asynclightning.mixins;

import eu.mikroskeem.asynclightning.interfaces.AsyncLightingChunk;
import eu.mikroskeem.asynclightning.interfaces.AsyncLightingWorld;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.EnumSkyBlock;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.MCUtil;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mark Vainomaa
 */
@Mixin(value = Chunk.class, remap = false)
public abstract class MixinChunk_AsyncLighting implements AsyncLightingChunk {
    private final static String ASYNC$World_getHeight = "Lnet/minecraft/server/v1_12_R1/World;getHighestBlockYAt(" +
            "Lnet/minecraft/server/v1_12_R1/BlockPosition;)Lnet/minecraft/server/v1_12_R1/BlockPosition;";
    private final static String ASYNC$World_isAreaLoaded = "Lnet/minecraft/server/v1_12_R1/World;areChunksLoaded(" +
            "Lnet/minecraft/server/v1_12_R1/BlockPosition;I)Z";
    private final static String ASYNC$World_checkLightFor = "Lnet/minecraft/server/v1_12_R1/World;c(" +
            "Lnet/minecraft/server/v1_12_R1/EnumSkyBlock;Lnet/minecraft/server/v1_12_R1/BlockPosition;)Z";
    private final static String ASYNC$World_getBlockState = "Lnet/minecraft/server/v1_12_R1/World;o(" +
            "Lnet/minecraft/server/v1_12_R1/BlockPosition;)Lnet/minecraft/server/v1_12_R1/IBlockData;";
    private final static String ASYNC$World_checkLight = "Lnet/minecraft/server/v1_12_R1/World;w(" +
            "Lnet/minecraft/server/v1_12_R1/BlockPosition;)Z";


    private Chunk[] chunkNeighbors = new Chunk[4]; // TODO: populate somehow?

    // Keeps track of block positions in this chunk currently queued for sky light update
    private CopyOnWriteArrayList<Short> queuedSkyLightingUpdates = new CopyOnWriteArrayList<>();
    // Keeps track of block positions in this chunk currently queued for block light update
    private CopyOnWriteArrayList<Short> queuedBlockLightingUpdates = new CopyOnWriteArrayList<>();
    private AtomicInteger pendingLightUpdates = new AtomicInteger();
    private long lightUpdateTime;
    private ExecutorService lightExecutorService;
    private static final List<Chunk> EMPTY_LIST = new ArrayList<>();
    private static final BlockPosition DUMMY_POS = new BlockPosition(0, 0, 0);

    @Shadow @Final public int locZ; // MCP - x
    @Shadow @Final public int locX; // MCP - z
    @Shadow private boolean s; // MCP - dirty
    @Shadow @Nullable public abstract TileEntity a(BlockPosition blockposition, Chunk.EnumTileEntityState chunk_enumtileentitystate); // MCP - getTileEntity()
    @Shadow @Final private ConcurrentLinkedQueue<BlockPosition> y; // MCP - tileEntityPosQueue
    @Shadow @Final private ChunkSection[] sections; // MCP - storageArrays
    @Shadow @Final private boolean[] i; // MCP - updateSkylightColumns
    @Shadow private int v; // MCP - heightMapMinimum
    @Shadow protected abstract void a(int i, int j, int k, int l); // MCP - updateSkylightNeighborHeight()
    @Shadow protected abstract int d(int i, int j, int k); // MCP - getBlockLightOpacity(...)
    @Shadow public abstract int b(int i, int j); // MCP - getHeightValue
    @Shadow public abstract int w(); // MCP - getLowestHeight
    @Shadow public boolean d; // MCP - unloadQueued
    @Shadow private boolean m; // MCP - isGapLightingUpdate
    @Shadow private boolean r; // MCP - ticked
    @Shadow private boolean lit; // MCP - isLightPopulated
    @Shadow private boolean done; // MCP - isTerrainPopulated
    @Shadow public abstract IBlockData getBlockData(BlockPosition pos); // MCP - getBlockState()
    @Shadow @Nullable protected abstract TileEntity g(BlockPosition blockposition); // MCP - createNewTileEntity()
    @Shadow protected abstract void b(int i, int j, int k); // MCP - checkSkylightNeighborHeight()
    @Shadow public abstract int g(); // MCP - getTopFilledSegment()
    @Shadow protected abstract void z(); // MCP - setSkylightUpdated()
    @Shadow protected abstract void a(EnumDirection direction); // MCP - checkLightSide()

    @Shadow @Final public int[] heightMap;
    @Shadow @Final public World world;
    @Shadow public abstract ChunkSection[] getSections();

    @Inject(method = "<init>(Lnet/minecraft/server/v1_12_R1/World;II)V", at = @At("RETURN"))
    private void onConstruct(World world, int x, int z, CallbackInfo ci) {
        if(!world.isClientSide) {
            this.lightExecutorService = ((AsyncLightingWorld) world).getLightingExecutor();
        }
    }

    // MCP - target: onTick -> b
    @Inject(method = "b(Z)V", at = @At("HEAD"), cancellable = true)
    private void onTickHead(boolean skipRecheckGaps, CallbackInfo ci) {
        if (!this.world.isClientSide) {
            final List<Chunk> neighbors = this.getSurroundingChunks();
            if (this.m && this.world.worldProvider.m() && !skipRecheckGaps && !neighbors.isEmpty()) {
                this.lightExecutorService.execute(() -> this.recheckGapsAsync(neighbors));
                // MCP - isGapLightingUpdate
                this.m = false;
            }

            this.r = true;

            if (!this.lit && this.done && !neighbors.isEmpty()) {
                this.lightExecutorService.execute(() -> {
                    this.checkLightAsync(neighbors);
                });
                // set to true to avoid requeuing the same task when not finished
                this.lit = true;
            }

            while (!this.y.isEmpty()) {
                BlockPosition blockpos = this.y.poll();

                // MCP - getTileEntity(...) MCP - Chunk.EnumCreateEntityType                                           MCP - hasTileEntity()
                if(this.a(blockpos, Chunk.EnumTileEntityState.CHECK) == null && this.getBlockData(blockpos).getBlock().isTileEntity()) {
                    // MCP - createNewTileEntity
                    TileEntity tileentity = this.g(blockpos);
                    this.world.setTileEntity(blockpos, tileentity);
                    // MCP - markBlockRangeForRenderUpdate
                    this.world.b(blockpos, blockpos);
                }
            }
            ci.cancel();
        }
    }

    // MCP - target: checkSkylightNeighborHeight -> b
    @Redirect(method = "b(III)V", at = @At(value = "INVOKE", target = ASYNC$World_getHeight))
    private BlockPosition onCheckSkylightGetHeight(World world, BlockPosition pos) {
        final Chunk chunk = this.getLightChunk(pos.getX() >> 4, pos.getZ() >> 4, null);
        if (chunk == null) {
            return DUMMY_POS;
        }

        //                                         MCP - getHeightValue
        return new BlockPosition(pos.getX(), chunk.b(pos.getX() & 15, pos.getZ() & 15), pos.getZ());
    }

    // MCP - target: updateSkylightNeighborHeight -> a
    @Redirect(method = "a(IIII)V", at = @At(value = "INVOKE", target = ASYNC$World_isAreaLoaded))
    private boolean onAreaLoadedSkyLightNeighbor(World world, BlockPosition pos, int radius) {
        return this.isAreaLoaded();
    }

    // MCP - target: updateSkylightNeighborHeight -> a
    @Redirect(method = "a(IIII)V", at = @At(value = "INVOKE", target = ASYNC$World_checkLightFor))
    private boolean onCheckLightForSkylightNeighbor(World world, EnumSkyBlock enumSkyBlock, BlockPosition pos) {
        if(world.isClientSide) {
            // MCP - checkLightFor
            return world.c(enumSkyBlock, pos);
        }

        return this.checkWorldLightFor(enumSkyBlock, pos);
    }

    /**
     * Rechecks chunk gaps async.
     *
     * @param neighbors A thread-safe list of surrounding neighbor chunks
     */
    private void recheckGapsAsync(List<Chunk> neighbors) {
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                // MCP - updateSkylightColumns
                if (this.i[i + j * 16]) {
                    this.i[i + j * 16] = false;
                    int k = this.b(i, j);
                    int l = this.locX * 16 + i;
                    int i1 = this.locZ * 16 + j;
                    int j1 = Integer.MAX_VALUE;

                    for (EnumDirection enumfacing : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
                        final Chunk chunk = this.getLightChunk((l + enumfacing.getAdjacentX()) >> 4, (i1 + enumfacing.getAdjacentZ()) >> 4, neighbors);
                        if (chunk == null || chunk.d) {
                            continue;
                        }
                        //                MCP - getLowestHeight
                        j1 = Math.min(j1, chunk.w());
                    }

                    // MCP - checkSkylightNeighborHeight
                    this.b(l, i1, j1);
                    
                    //                               MCP - EnumFacing.Plane
                    for (EnumDirection enumfacing1 : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
                        this.b(l + enumfacing1.getAdjacentX(), i1 + enumfacing1.getAdjacentZ(), k);
                    }
                }
            }

            // this.isGapLightingUpdated = false;
        }
    }

    // MCP - IBlockState -> IBlockData, target: n -> enqueueRelightChecks
    @Redirect(method = "n", at = @At(value = "INVOKE", target = ASYNC$World_getBlockState))
    private IBlockData onRelightChecksGetBlockState(World world, BlockPosition pos) {
        //Chunk chunk = world.getChunkProvider().getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);
        Chunk chunk = MCUtil.getLoadedChunkWithoutMarkingActive(world.getChunkProvider(), pos.getX() >> 4, pos.getZ() >> 4);
        

        final AsyncLightingChunk asyncLightingChunk = (AsyncLightingChunk) chunk;
        //                   MCP - unloadQueued
        if (chunk == null || chunk.d || !asyncLightingChunk.areNeighborsLoaded()) {
            return Blocks.AIR.getBlockData();
        }

        // MCP - getBlockState(...)
        return chunk.getBlockData(pos);
    }

    // MCP - target: enqueueRelightChecks -> n
    @Redirect(method = "n", at = @At(value = "INVOKE", target = ASYNC$World_checkLight))
    private boolean onRelightChecksCheckLight(World world, BlockPosition pos) {
        if (!this.world.isClientSide) {
            return this.checkWorldLight(pos);
        }

        // MCP - checkLight(...)
        return world.w(pos);
    }

    // Avoids grabbing chunk async during light check
    // MCP - target: checkLight -> e
    @Redirect(method = "e(II)Z", at = @At(value = "INVOKE", target = ASYNC$World_checkLight))
    private boolean onCheckLightWorld(World world, BlockPosition pos) {
        if (!world.isClientSide) {
            return this.checkWorldLight(pos);
        }
        // MCP - checkLight(...)
        return world.w(pos);
    }

    // MCP - target: checkLight -> o
    @Inject(method = "o", at = @At("HEAD"), cancellable = true)
    private void checkLightHead(CallbackInfo ci) {
        if (!this.world.isClientSide) {
            if (this.world.getMinecraftServer().isStopped() || this.lightExecutorService.isShutdown()) {
                return;
            }

            // MCP - isQueuedForUnload() -> unloadQueued
            if (this.d) {
                return;
            }
            final List<Chunk> neighborChunks = this.getSurroundingChunks();
            if (neighborChunks.isEmpty()) {
                this.lit = false;
                return;
            }
            
            if(Thread.currentThread() != this.world.getMinecraftServer().primaryThread) {
                this.lightExecutorService.execute(() ->
                    this.checkLightAsync(neighborChunks)
                );
            } else {
                this.checkLightAsync(neighborChunks);
            }
            ci.cancel();
        }
    }

    /**
     * Checks light async.
     *
     * @param neighbors A thread-safe list of surrounding neighbor chunks
     */
    private void checkLightAsync(List<Chunk> neighbors) {
        this.done = true;
        this.lit = true;
        BlockPosition blockpos = new BlockPosition(this.locX << 4, 0, this.locZ << 4);

        if (this.world.worldProvider.m()) {
            label44:

            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    if (!this.checkLightAsync(i, j, neighbors)) {
                        this.lit = false;
                        break label44;
                    }
                }
            }

            if (this.lit) {
                for (EnumDirection enumfacing : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
                    //   MCP - getAxisDirection   MCP - EnumFacing.AxisDirection.POSITIVE
                    int k = enumfacing.c() == EnumDirection.EnumAxisDirection.POSITIVE ? 16 : 1;
                    //                                 MCP - offset
                    final BlockPosition pos = blockpos.shift(enumfacing, k);
                    final Chunk chunk = this.getLightChunk(pos.getX() >> 4, pos.getZ() >> 4, neighbors);
                    if (chunk == null) {
                        continue;
                    }
                    // MCP - checkLightSide(...) MCP - getOpposite()
                    EnumDirection opposite = enumfacing.opposite();
                    //chunk.a(opposite);
                    ((AsyncLightingChunk) chunk).checkLightSide(opposite);
                }

                this.z(); // MCP - setSkylightUpdated()
            }
        }
    }

    /**
     * Checks light async.
     *
     * @param x The x position of chunk
     * @param z The z position of chunk
     * @param neighbors A thread-safe list of surrounding neighbor chunks
     * @return True if light update was successful, false if not
     */
    private boolean checkLightAsync(int x, int z, List<Chunk> neighbors) {
        int i = this.g(); // MCP - getTopFilledSegment()
        boolean flag = false;
        boolean flag1 = false;
        BlockPosition.MutableBlockPosition blockpos$mutableblockpos = new BlockPosition.MutableBlockPosition((this.locX << 4) + x, 0, (this.locZ << 4) + z);

        for (int j = i + 16 - 1; j > this.world.getSeaLevel() || j > 0 && !flag1; --j) {
            blockpos$mutableblockpos.a(blockpos$mutableblockpos.getX(), j, blockpos$mutableblockpos.getZ());
            //                                                  MCP - getLightOpacity()
            int k = this.getBlockData(blockpos$mutableblockpos).c();

            if (k == 255 && blockpos$mutableblockpos.getY() < this.world.getSeaLevel()) {
                flag1 = true;
            }

            if (!flag && k > 0) {
                flag = true;
            } else if (flag && k == 0 && !this.checkWorldLight(blockpos$mutableblockpos, neighbors)) {
                return false;
            }
        }

        for (int l = blockpos$mutableblockpos.getY(); l > 0; --l) {
            blockpos$mutableblockpos.a(blockpos$mutableblockpos.getX(), l, blockpos$mutableblockpos.getZ());

            if (this.getBlockData(blockpos$mutableblockpos).c() > 0) {
                this.checkWorldLight(blockpos$mutableblockpos, neighbors);
            }
        }

        return true;
    }

    /**
     * Thread-safe method to retrieve a chunk during async light updates.
     *
     * @param chunkX The x position of chunk.
     * @param chunkZ The z position of chunk.
     * @param neighbors A thread-safe list of surrounding neighbor chunks
     * @return The chunk if available, null if not
     */
    private Chunk getLightChunk(int chunkX, int chunkZ, List<Chunk> neighbors) {
        final Chunk currentChunk = (Chunk)(Object) this;
        // MCP - isAtLocation
        if(currentChunk.a(chunkX, chunkZ)) {
            if (currentChunk.d) {
                return null;
            }
            return currentChunk;
        }
        if (neighbors == null) {
            neighbors = this.getSurroundingChunks();
            if (neighbors.isEmpty()) {
                return null;
            }
        }
        for(Chunk neighbor : neighbors) {
            if (neighbor.a(chunkX, chunkZ)) {
                if (neighbor.d) {
                    return null;
                }
                return neighbor;
            }
        }

        return null;
    }

    /**
     * Checks if surrounding chunks are loaded thread-safe.
     *
     * @return True if surrounded chunks are loaded, false if not
     */
    private boolean isAreaLoaded() {
        if (!this.areNeighborsLoaded()) {
            return false;
        }

        // add diagonal chunks
        final Chunk southEastChunk = ((AsyncLightingChunk) this.getNeighborChunk(0)).getNeighborChunk(2);
        if (southEastChunk == null) {
            return false;
        }

        final Chunk southWestChunk = ((AsyncLightingChunk) this.getNeighborChunk(0)).getNeighborChunk(3);
        if (southWestChunk == null) {
            return false;
        }

        final Chunk northEastChunk = ((AsyncLightingChunk) this.getNeighborChunk(1)).getNeighborChunk(2);
        if (northEastChunk == null) {
            return false;
        }

        final Chunk northWestChunk = ((AsyncLightingChunk) this.getNeighborChunk(1)).getNeighborChunk(3);
        if (northWestChunk == null) {
            return false;
        }

        return true;
    }

    /**
     * Gets surrounding chunks thread-safe.
     *
     * @return The list of surrounding chunks, empty list if not loaded
     */
    private List<Chunk> getSurroundingChunks() {
        if (!this.areNeighborsLoaded()) {
            return EMPTY_LIST;
        }

        // add diagonal chunks
        final Chunk southEastChunk = ((AsyncLightingChunk) this.getNeighborChunk(0)).getNeighborChunk(2);
        if (southEastChunk == null) {
            return EMPTY_LIST;
        }

        final Chunk southWestChunk = ((AsyncLightingChunk) this.getNeighborChunk(0)).getNeighborChunk(3);
        if (southWestChunk == null) {
            return EMPTY_LIST;
        }

        final Chunk northEastChunk = ((AsyncLightingChunk) this.getNeighborChunk(1)).getNeighborChunk(2);
        if (northEastChunk == null) {
            return EMPTY_LIST;
        }

        final Chunk northWestChunk = ((AsyncLightingChunk) this.getNeighborChunk(1)).getNeighborChunk(3);
        if (northWestChunk == null) {
            return EMPTY_LIST;
        }

        List<Chunk> chunkList = new ArrayList<>();
        chunkList = this.getNeighbors();
        chunkList.add(southEastChunk);
        chunkList.add(southWestChunk);
        chunkList.add(northEastChunk);
        chunkList.add(northWestChunk);
        return chunkList;
    }

    // MCP - target: relightBlock -> c
    @Inject(method = "c(III)V", at = @At("HEAD"), cancellable = true)
    private void onRelightBlock(int x, int y, int z, CallbackInfo ci) {
        if (!this.world.isClientSide) {
            this.lightExecutorService.execute(() -> {
                this.relightBlockAsync(x, y, z);
            });
            ci.cancel();
        }
    }

    /**
     * Relight's a block async.
     *
     * @param x The x position
     * @param y The y position
     * @param z The z position
     */
    private void relightBlockAsync(int x, int y, int z) {
        int i = this.heightMap[z << 4 | x] & 255;
        int j = i;

        if (y > i)
        {
            j = y;
        }

        //                   MCP - getBlockLightOpacity(...)
        while (j > 0 && this.d(x, j - 1, z) == 0) {
            --j;
        }

        if (j != i)
        {
            this.markBlocksDirtyVerticalAsync(x + this.locX * 16, z + this.locZ * 16, j, i);
            this.heightMap[z << 4 | x] = j;
            int k = this.locX * 16 + x;
            int l = this.locZ * 16 + z;

            if (this.world.worldProvider.m()) {
                if (j < i) {
                    for (int j1 = j; j1 < i; ++j1) {
                        // MCP - ExtendedBlockStorage        MCP - storageArrays
                        ChunkSection extendedblockstorage2 = this.sections[j1 >> 4];

                        //                           MCP - Chunk.NULL_BLOCK_STORAGE
                        if (extendedblockstorage2 != Chunk.EMPTY_CHUNK_SECTION) {
                            // MCP - setSkyLight(...)
                            extendedblockstorage2.a(x, j1 & 15, z, 15);
                            // MCP - notifyLightSet(...)
                            this.world.m(new BlockPosition((this.locX << 4) + x, j1, (this.locZ << 4) + z));
                        }
                    }
                } else {
                    for (int i1 = i; i1 < j; ++i1) {
                        ChunkSection extendedblockstorage = this.sections[i1 >> 4];

                        if (extendedblockstorage != Chunk.EMPTY_CHUNK_SECTION) {
                            // MCP - setSkyLight(...)
                            extendedblockstorage.a(x, i1 & 15, z, 0);
                            this.world.m(new BlockPosition((this.locX << 4) + x, i1, (this.locZ << 4) + z));
                        }
                    }
                }

                int k1 = 15;

                while (j > 0 && k1 > 0) {
                    --j;
                    // MCP - getBlockLightOpacity(...)
                    int i2 = this.d(x, j, z);

                    if (i2 == 0) {
                        i2 = 1;
                    }

                    k1 -= i2;

                    if (k1 < 0) {
                        k1 = 0;
                    }

                    ChunkSection extendedblockstorage1 = this.sections[j >> 4];

                    if (extendedblockstorage1 != Chunk.EMPTY_CHUNK_SECTION) {
                        extendedblockstorage1.b(x, j & 15, z, k1);
                    }
                }
            }

            int l1 = this.heightMap[z << 4 | x];
            int j2 = i;
            int k2 = l1;

            if (l1 < i) {
                j2 = l1;
                k2 = i;
            }

            // MCP - heightMapMinimum
            if (l1 < this.v) {
                this.v = l1;
            }

            if (this.world.worldProvider.m()) {
                for (EnumDirection enumfacing : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
                    this.a(k + enumfacing.getAdjacentX(), l + enumfacing.getAdjacentZ(), j2, k2);
                }

                this.a(k, l, j2, k2);
            }

            //this.dirty = true;
            this.s = true;
        }
    }

    /**
     * Marks a vertical line of blocks as dirty async.
     * Instead of calling world directly, we pass chunk safely for async light method.
     *
     * @param x1
     * @param z1
     * @param x2
     * @param z2
     */
    private void markBlocksDirtyVerticalAsync(int x1, int z1, int x2, int z2) {
        if (x2 > z2) {
            int i = z2;
            z2 = x2;
            x2 = i;
        }

        // MCP - hasSkyLight()
        if(this.world.worldProvider.m()) {
            for (int j = x2; j <= z2; ++j) {
                final BlockPosition pos = new BlockPosition(x1, j, z1);
                final Chunk chunk = this.getLightChunk(pos.getX() >> 4, pos.getZ() >> 4, null);
                if (chunk == null) {
                    continue;
                }
                ((AsyncLightingWorld) this.world).updateLightAsync(EnumSkyBlock.SKY, new BlockPosition(x1, j, z1), chunk);
            }
        }

        // MCP - markBlockRangeForRenderUpdate
        this.world.b(x1, x2, z1, x1, z2, z1);
    }

    /**
     * Checks world light thread-safe.
     *
     * @param lightType The type of light to check
     * @param pos The block position
     * @return True if light update was successful, false if not
     */
    private boolean checkWorldLightFor(EnumSkyBlock lightType, BlockPosition pos) {
        final Chunk chunk = this.getLightChunk(pos.getX() >> 4, pos.getZ() >> 4, null);
        if (chunk == null) {
            return false;
        }

        return ((AsyncLightingWorld) this.world).updateLightAsync(lightType, pos, chunk);
    }

    private boolean checkWorldLight(BlockPosition pos) {
        return this.checkWorldLight(pos, null);
    }

    /**
     * Checks world light async.
     *
     * @param pos The block position
     * @param neighbors A thread-safe list of surrounding neighbor chunks
     * @return True if light update was successful, false if not
     */
    private boolean checkWorldLight(BlockPosition pos, List<Chunk> neighbors) {
        boolean flag = false;
        final Chunk chunk = this.getLightChunk(pos.getX() >> 4, pos.getZ() >> 4, neighbors);
        if (chunk == null) {
            return false;
        }

        if (this.world.worldProvider.m()) {
            flag |= ((AsyncLightingWorld) this.world).updateLightAsync(EnumSkyBlock.SKY, pos, chunk);
        }

        flag = flag | ((AsyncLightingWorld) this.world).updateLightAsync(EnumSkyBlock.BLOCK, pos, chunk);
        return flag;
    }

    @Override
    public AtomicInteger getPendingLightUpdates() {
        return pendingLightUpdates;
    }

    @Override
    public long getLightUpdateTime() {
        return this.lightUpdateTime;
    }

    @Override
    public void setLightUpdateTime(long time) {
        this.lightUpdateTime = time;
    }

    @Override
    public CopyOnWriteArrayList<Short> getQueuedLightingUpdates(EnumSkyBlock type) {
        if(type == EnumSkyBlock.SKY) {
            return this.queuedSkyLightingUpdates;
        } //else if(type == EnumSkyBlock.BLOCK) {
        return this.queuedBlockLightingUpdates;
        //}
    }

    @Override
    public void checkLightSide(EnumDirection direction) {
        this.a(direction);
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
}
