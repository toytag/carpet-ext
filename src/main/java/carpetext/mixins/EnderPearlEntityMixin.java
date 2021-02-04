package carpetext.mixins;

// MIXIN
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// JAVA
import java.io.IOException;
import java.util.Comparator;

// MINECRAFT
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

// CARPET-EXT
import carpetext.CarpetExtSettings;


@Mixin(EnderPearlEntity.class)
public abstract class EnderPearlEntityMixin extends ThrownEntity {
    private static final ChunkTicketType<ChunkPos> ENDER_PEARL_TICKET = 
        ChunkTicketType.create("ender_pearl", Comparator.comparingLong(ChunkPos::toLong), 2);

    private boolean local_sync = true;
    private Vec3d local_pos = null;
    private Vec3d local_velocity = null;

    protected EnderPearlEntityMixin(EntityType<? extends ThrownEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void chunkLoadNextChunk(CallbackInfo ci) {
        if (CarpetExtSettings.enderPearlChunkLoadingAndSkipping) {
            this.chunkLoading(this);
        }
    }

    private void chunkLoading(Entity entity) {
        World world = entity.getEntityWorld();

        Vec3d pos = entity.getPos();
        Vec3d velocity = entity.getVelocity();

        if (this.local_sync) {
            this.local_pos = new Vec3d(pos.x, pos.y, pos.z);
            this.local_velocity = new Vec3d(velocity.x, velocity.y, velocity.z);
        }

        // debug
        // System.out.printf("[x: %f, y: %f, z: %f | local_x: %f, local_y: %f, local_z: %f | vx: %f, vy: %f, vz: %f | local_vx: %f, local_vy: %f, local_vz: %f]\n",
        //     pos.x, pos.y, pos.z, this.local_pos.x, this.local_pos.y, this.local_pos.z, velocity.x, velocity.y, velocity.z, this.local_velocity.x, this.local_velocity.y, this.local_velocity.z);

        ChunkPos currentChunkPos = new ChunkPos(MathHelper.floor(this.local_pos.x) >> 4,
                MathHelper.floor(this.local_pos.z) >> 4);
        ChunkPos nextChunkPos = new ChunkPos(MathHelper.floor(this.local_pos.x + this.local_velocity.x) >> 4,
                MathHelper.floor(this.local_pos.z + this.local_velocity.z) >> 4);

        if (world instanceof ServerWorld) {
            ServerChunkManager serverChunkManager = ((ServerWorld) world).getChunkManager();
            serverChunkManager.addTicket(ENDER_PEARL_TICKET, currentChunkPos, 2, currentChunkPos);
            boolean skipNextChunkLoading = false;
            if (this.local_velocity.length() > 1) {
                try {
                    CompoundTag compoundTag = serverChunkManager.threadedAnvilChunkStorage.getNbt(nextChunkPos);
                    skipNextChunkLoading = this.shouldSkipNextChunkLoading(compoundTag);
                } catch (IOException e) {
                    // Auto-generated catch block
                    skipNextChunkLoading = true;
                    e.printStackTrace();
                }
                
                // debug
                // System.out.println("skipNextChunkLoading: " + skipNextChunkLoading);

                if (skipNextChunkLoading) {
                    entity.setVelocity(0, 0, 0);
                    this.local_pos = this.local_pos.add(this.local_velocity);
                    this.local_velocity = this.local_velocity.multiply(0.99).subtract(0, (double) this.getGravity(), 0);
                    this.local_sync = false;
                } else {
                    entity.setPos(this.local_pos.x, this.local_pos.y, this.local_pos.z);
                    entity.setVelocity(this.local_velocity.x, this.local_velocity.y, this.local_velocity.z);
                    serverChunkManager.addTicket(ENDER_PEARL_TICKET, nextChunkPos, 2, nextChunkPos);
                    this.local_sync = true;
                }
            }
        }

        if (this.local_pos.y <= 0) {
            this.remove();
        }
    }
    
    private boolean shouldSkipNextChunkLoading(CompoundTag compoundTag) {
        boolean chunkStatusFull = compoundTag != null 
            && compoundTag.contains("Level", 10)
            && compoundTag.getCompound("Level").contains("Heightmaps", 10)
            && compoundTag.getCompound("Level").getCompound("Heightmaps").contains("MOTION_BLOCKING", 12);

        // debug
        // System.out.println("chunkStatusFull: " + chunkStatusFull);
        
        if (chunkStatusFull) {
            // chunk exists and has been generated before
            long[] array = compoundTag.getCompound("Level").getCompound("Heightmaps").getLongArray("MOTION_BLOCKING");

            // debug
            // System.out.println("array.length: " + array.length);

            if (array.length != 37) {
                // should have 37 elements after vanilla 1.16
                return true;
            } else {
                // find highest motion blocking block y pos
                long highest_y = 0;
                for (long element : array) {
                    for (int i = 0; i < 7; i++) {
                        long height = element & 0b111111111;
                        if (height > highest_y) highest_y = height;
                        element = element >> 9;
                    }
                }

                // debug
                // System.out.println("Highest y: " + highest_y);

                // if next y pos > highest motion blocking block y pos, skip chunk loading
                return this.local_pos.y + this.local_velocity.y > highest_y;
            }
        } else {
            // chunk does not exists or has never been generated before
            return true;
        }
    }

}
