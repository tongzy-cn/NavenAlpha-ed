package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.FallingPlayer;
import com.heypixel.heypixelmod.obsoverlay.utils.InventoryUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.TimeHelper;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.Rotation;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        name = "AutoMLG",
        cnName = "自动落地",
        description = "Automatically place water when falling",
        category = Category.MOVEMENT
)
public class AutoMLG extends Module {
    private final FloatValue fallDistance = ValueBuilder.create(this, "Fall Distance")
            .setDefaultFloatValue(10.0F)
            .setFloatStep(0.1F)
            .setMinFloatValue(3.0F)
            .setMaxFloatValue(20.0F)
            .build()
            .getFloatValue();
    private final FloatValue placeDistance = ValueBuilder.create(this, "Place Distance")
            .setDefaultFloatValue(4.5F)
            .setFloatStep(0.1F)
            .setMinFloatValue(2.0F)
            .setMaxFloatValue(8.0F)
            .build()
            .getFloatValue();
    private final FloatValue rotationSpeed = ValueBuilder.create(this, "Rotation Speed")
            .setDefaultFloatValue(180.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(60.0F)
            .setMaxFloatValue(180.0F)
            .build()
            .getFloatValue();
    private final FloatValue predictTicks = ValueBuilder.create(this, "Predict Ticks")
            .setDefaultFloatValue(6.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(20.0F)
            .build()
            .getFloatValue();
    private final FloatValue delay = ValueBuilder.create(this, "Delay")
            .setDefaultFloatValue(150.0F)
            .setFloatStep(10.0F)
            .setMinFloatValue(50.0F)
            .setMaxFloatValue(1000.0F)
            .build()
            .getFloatValue();
    private final TimeHelper timer = new TimeHelper();
    private int placeTicks;
    private BlockPos targetPos;
    private Direction targetFace;
    private int swapBack = -1;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    public void onMotion(EventMotion e) {
        if (e.getType() != EventType.PRE || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (Naven.getInstance().getModuleManager().getModule(Scaffold.class).isEnabled()
                || Naven.getInstance().getModuleManager().getModule(Stuck.class).isEnabled()) {
            resetState();
            return;
        }

        if (mc.player.onGround() || mc.player.isInWater() || mc.player.isInLava()) {
            resetState();
            return;
        }

        Integer waterSlot = InventoryUtils.findItemHotbar(Items.WATER_BUCKET);
        if (waterSlot == null) {
            resetState();
            return;
        }

        if (placeTicks > 0) {
            placeTicks--;
            if (placeTicks == 0) {
                tryPlaceWater();
            }
            return;
        }

        if (mc.player.fallDistance < fallDistance.getCurrentValue() || !timer.delay(delay.getCurrentValue())) {
            return;
        }

        BlockHitResult hit = findTargetHit();
        if (hit == null) {
            return;
        }

        double distanceToHit = mc.player.getEyePosition().distanceTo(hit.getLocation());
        if (distanceToHit > placeDistance.getCurrentValue()) {
            return;
        }

        Direction face = hit.getDirection();
        if (face == Direction.DOWN) {
            face = Direction.UP;
        }

        BlockPos hitPos = hit.getBlockPos();
        BlockPos placePos = hitPos.relative(face);
        if (!mc.level.getBlockState(placePos).isAir()) {
            return;
        }

        targetPos = hitPos;
        targetFace = face;
        Rotation rotation = RotationUtils.calculate(hitPos, face);
        RotationManager.setRotations(rotation, rotationSpeed.getCurrentValue());
        placeTicks = 2;
        timer.reset();
    }

    private void tryPlaceWater() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (mc.player.onGround() || mc.player.isInWater() || mc.player.isInLava()) {
            resetState();
            return;
        }

        Integer waterSlot = InventoryUtils.findItemHotbar(Items.WATER_BUCKET);
        if (waterSlot == null) {
            resetState();
            return;
        }

        if (targetPos == null || targetFace == null) {
            return;
        }

        int originalSlot = mc.player.getInventory().selected;
        if (originalSlot != waterSlot) {
            swapBack = originalSlot;
            mc.player.getInventory().selected = waterSlot;
        }

        InteractionResult interactionResult = mc.gameMode.useItemOn(
                mc.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Scaffold.getVec3(targetPos, targetFace), targetFace, targetPos, false)
        );
        if (interactionResult == InteractionResult.SUCCESS) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        if (swapBack != -1) {
            mc.player.getInventory().selected = swapBack;
            swapBack = -1;
        }

        targetPos = null;
        targetFace = null;
    }

    private BlockHitResult findTargetHit() {
        int ticks = Math.max(1, (int) predictTicks.getCurrentValue());
        FallingPlayer fallingPlayer = new FallingPlayer(mc.player);
        fallingPlayer.calculateMLG(ticks);

        Vec3 start = new Vec3(fallingPlayer.x, fallingPlayer.y, fallingPlayer.z);
        Vec3 end = new Vec3(start.x, mc.level.getMinBuildHeight() - 2.0, start.z);
        HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return (BlockHitResult) hit;
    }

    private void resetState() {
        placeTicks = 0;
        targetPos = null;
        targetFace = null;
        swapBack = -1;
    }
}
