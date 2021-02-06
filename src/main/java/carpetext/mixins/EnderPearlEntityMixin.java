package carpetext.mixins;

// CARPET-EXT
import carpetext.CarpetExtSettings;

// JAVA
import java.io.IOException;
import java.util.Comparator;

// MINECRAFT
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

// MIXIN
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EnderPearlEntity.class)
public abstract class EnderPearlEntityMixin extends ThrownItemEntity {
    private static final ChunkTicketType<ChunkPos> ENDER_PEARL_TICKET = 
        ChunkTicketType.create("ender_pearl", Comparator.comparingLong(ChunkPos::toLong), 2);
    
    private boolean sync = true;
    private Vec3d fakePos = null;
    private Vec3d fakeVelocity = null;
    private Vec3d realPos = null;
    private Vec3d realVelocity = null;

    protected EnderPearlEntityMixin(EntityType<? extends ThrownItemEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void preprocess(CallbackInfo ci) {
        if (CarpetExtSettings.enderPearlSkippyChunkLoading) {
            this.skippyChunkLoading();
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void postprocess(CallbackInfo ci) {
        if (CarpetExtSettings.enderPearlSkippyChunkLoading) {
            this.shouldStayPut();
        }
    }

    // preprocess main function
    private void skippyChunkLoading() {
        World world = this.getEntityWorld();

        if (world instanceof ServerWorld) {
            this.fakePos = this.getPos().add(Vec3d.ZERO);
            this.fakeVelocity = this.getVelocity().add(Vec3d.ZERO);
            
            if (this.sync) {
                this.realPos = this.fakePos.add(Vec3d.ZERO);
                this.realVelocity = this.fakeVelocity.add(Vec3d.ZERO);
            }
            
            // forward real pos and velocity
            this.realPos = this.realPos.add(this.realVelocity);
            this.realVelocity = this.realVelocity.multiply(0.99F).subtract(0, this.getGravity(), 0);
            if (this.realPos.y <= 0) this.remove();
            
            // debug
            System.out.println("fake:" + this.fakePos + this.fakeVelocity + "real:" + this.realPos + this.realVelocity);
            
            ChunkPos fakeChunkPos = new ChunkPos(
                MathHelper.floor(this.fakePos.x) >> 4, MathHelper.floor(this.fakePos.z) >> 4);
            ChunkPos realChunkPos = new ChunkPos(
                MathHelper.floor(this.realPos.x) >> 4, MathHelper.floor(this.realPos.z) >> 4);

            // chunk loading
            ServerChunkManager serverChunkManager = ((ServerWorld) world).getChunkManager();
            System.out.println("shouldTickChunk(realChunkPos): " + serverChunkManager.shouldTickChunk(realChunkPos));
            if (!serverChunkManager.shouldTickChunk(realChunkPos)) {
                boolean shouldSkipChunkLoading = false;
                try {
                    // chunk skipping
                    CompoundTag compoundTag = serverChunkManager.threadedAnvilChunkStorage.getNbt(realChunkPos);
                    shouldSkipChunkLoading = this.checkChunkNbtTag(compoundTag);
                } catch (IOException e) {
                    // auto-generated catch block
                    shouldSkipChunkLoading = true;
                    System.out.println("getNbt IOException");
                    e.printStackTrace();
                }
                
                // debug
                System.out.println("skipChunkLoading: " + shouldSkipChunkLoading);
                
                if (shouldSkipChunkLoading) {
                    // stay put (done at tick TAIL)
                    serverChunkManager.addTicket(ENDER_PEARL_TICKET, fakeChunkPos, 2, fakeChunkPos);
                    this.sync = false;
                } else {
                    // move
                    serverChunkManager.addTicket(ENDER_PEARL_TICKET, realChunkPos, 2, realChunkPos);
                    this.setVelocity(this.realVelocity);
                    this.updatePosition(this.realPos);
                    this.sync = true;
                }
            } else {
                if (!this.sync) {
                    // move
                    this.setVelocity(this.realVelocity);
                    this.updatePosition(this.realPos);
                    this.sync = true;
                }
            }
        }
    }

    // postprocess main function
    private void shouldStayPut() {
        World world = this.getEntityWorld();

        if (world instanceof ServerWorld) {
            if (!this.sync) {
                System.out.println("Out of sync");
                this.setVelocity(this.fakeVelocity);
                this.updatePosition(this.fakePos);
            }
        }
    }
    
    // helper function
    private boolean checkChunkNbtTag(CompoundTag compoundTag) {
        boolean chunkStatusFull = compoundTag != null 
            && compoundTag.contains("Level", 10)
            && compoundTag.getCompound("Level").contains("Heightmaps", 10)
            && compoundTag.getCompound("Level").getCompound("Heightmaps").contains("MOTION_BLOCKING", 12);

        // debug
        System.out.println("chunkStatusFull: " + chunkStatusFull);
        
        if (chunkStatusFull) {
            // chunk exists and has been generated before
            long[] array = compoundTag.getCompound("Level").getCompound("Heightmaps").getLongArray("MOTION_BLOCKING");

            
            if (array.length != 37) {
                // should have 37 elements after vanilla 1.16
                // debug
                System.out.println("array.length wrong: " + array.length);
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
                System.out.println("Highest y: " + highest_y);

                // if real y pos > highest motion blocking block y pos, skip chunk loading
                return this.realPos.y > highest_y && this.realPos.y + this.realVelocity.y > highest_y;
            }
        } else {
            // chunk does not exists or has never been generated before
            return true;
        }
    }

    // helper function
    private void updatePosition(Vec3d pos) {
        super.updatePosition(pos.x, pos.y, pos.z);
    }

}
