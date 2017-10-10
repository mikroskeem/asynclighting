package eu.mikroskeem.asynclightning.interfaces;

import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.EnumSkyBlock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mark Vainomaa
 */
public interface AsyncLightingChunk {
    CopyOnWriteArrayList<Short> getQueuedLightingUpdates(EnumSkyBlock type);
    AtomicInteger getPendingLightUpdates();
    long getLightUpdateTime();
    void setLightUpdateTime(long time);

    /* Exposed methods */
    void checkLightSide(EnumDirection direction);

    /* Ported from Sponge */
    boolean areNeighborsLoaded();
    Chunk getNeighborChunk(int index);
    List<Chunk> getNeighbors();
    void setNeighbor(int index, Chunk neighborChunk);
}
