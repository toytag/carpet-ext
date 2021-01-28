package carpetext.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.world.World;
import carpetext.CarpetExtSettings;
import carpetext.utils.ChunkUtils;

@Mixin(EnderPearlEntity.class)
public abstract class ThrownEntityMixin extends ThrownEntity {
    protected ThrownEntityMixin(EntityType<? extends ThrownEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void chunkLoadNextChunk(CallbackInfo ci) {
        if (CarpetExtSettings.enderPearlChunkLoading) {
            ChunkUtils.loadNextChunk(this);
        }
    }

}
