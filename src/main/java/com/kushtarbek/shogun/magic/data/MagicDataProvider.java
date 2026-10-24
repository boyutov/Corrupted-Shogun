package com.kushtarbek.shogun.magic.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MagicDataProvider implements ICapabilitySerializable<CompoundTag> {

    private final MagicData data;
    private final LazyOptional<MagicData> optional;

    public MagicDataProvider(Player player) {
        this.data = new MagicData(player);
        this.optional = LazyOptional.of(() -> data);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return MagicData.CAPABILITY.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.deserializeNBT(tag);
    }
}
