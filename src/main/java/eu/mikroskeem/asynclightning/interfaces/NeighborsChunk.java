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
