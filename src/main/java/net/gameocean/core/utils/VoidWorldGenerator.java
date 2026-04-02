package net.gameocean.core.utils;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Générateur de monde vide (Void)
 * Crée un monde sans aucun bloc
 */
public class VoidWorldGenerator extends ChunkGenerator {
    
    @Override
    public @NotNull ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z, @NotNull BiomeGrid biome) {
        // Retourne un chunk complètement vide
        return createChunkData(world);
    }
    
    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }
}