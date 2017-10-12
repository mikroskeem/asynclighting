package eu.mikroskeem.asynclightning.interfaces;

import java.util.List;

/**
 * Port of Sponge's Chunk neighbors system
 *
 * @author Mark Vainomaa
 */
public interface NeighborsChunk {
    boolean areNeighborsLoaded();
    net.minecraft.server.v1_12_R1.Chunk getNeighborChunk(int index);
    List<net.minecraft.server.v1_12_R1.Chunk> getNeighbors();
    void setNeighbor(int index, net.minecraft.server.v1_12_R1.Chunk neighborChunk);
}
