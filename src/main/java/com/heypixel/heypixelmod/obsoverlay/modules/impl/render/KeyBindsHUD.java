package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRenderSkia;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.skia.Skia;
import com.heypixel.heypixelmod.obsoverlay.utils.skia.font.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.skia.font.Icon;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.DragValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.humbleui.skija.Font;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(name = "KeyBinds", cnName = "按键显示", description = "显示所有按键绑定", category = Category.RENDER)
public class KeyBindsHUD extends Module {
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 100);
    private final DragValue dragValue = ValueBuilder.create(this, "Position")
            .setDefaultX(10f)
            .setDefaultY(80f)
            .build()
            .getDragValue();
    public FloatValue size = ValueBuilder.create(this, "KeyBinds Size")
            .setDefaultFloatValue(10.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(6.0F)
            .setMaxFloatValue(24.0F)
            .build()
            .getFloatValue();

    public ModeValue style = ValueBuilder.create(this, "Style")
            .setModes("Style A", "Style B")
            .setDefaultModeIndex(0)
            .build()
            .getModeValue();

    private final Map<Module, SmoothAnimationTimer> xTimers = new HashMap<>();
    private final Map<Module, SmoothAnimationTimer> yTimers = new HashMap<>();
    private final Map<Module, SmoothAnimationTimer> relativeYTimers = new HashMap<>();
    private final Map<Module, SmoothAnimationTimer> alphaTimers = new HashMap<>();

    private final SmoothAnimationTimer containerWidthTimer = new SmoothAnimationTimer(100, 100, 0.2f);
    private final SmoothAnimationTimer containerHeightTimer = new SmoothAnimationTimer(20, 20, 0.2f);

    @EventTarget
    public void onSkia(EventRenderSkia event) {
        if (style.isCurrentMode("Style A")) {
            renderStyleA();
        } else if (style.isCurrentMode("Style B")) {
            renderStyleB();
        }
    }

    private void renderStyleB() {
        Font font = Fonts.getMiSans(size.getCurrentValue());
        Font iconFont = Fonts.getIconFill(size.getCurrentValue());
        List<Module> modules = new ArrayList<>(Naven.getInstance().getModuleManager().getModules());
        boolean inEditor = mc.screen instanceof HUDEditor;
        float height = size.getCurrentValue() * 1.5f;
        float margin = size.getCurrentValue() / 3.0f;

        boolean topHalf = dragValue.getY() < mc.getWindow().getGuiScaledHeight() / 2.0f;
        modules.sort((m1, m2) -> {
            float width1 = 0;
            if (m1 != this && m1.getKey() != 0) {
                float nameW = Skia.getStringWidth(m1.getName(), font);
                float keyW = Skia.getStringWidth(getKeyName(m1.getKey()), font) + margin * 2.0f;
                width1 = nameW + margin * 2.0f + margin + keyW;
            }

            float width2 = 0;
            if (m2 != this && m2.getKey() != 0) {
                float nameW = Skia.getStringWidth(m2.getName(), font);
                float keyW = Skia.getStringWidth(getKeyName(m2.getKey()), font) + margin * 2.0f;
                width2 = nameW + margin * 2.0f + margin + keyW;
            }

            return topHalf ? Float.compare(width2, width1) : Float.compare(width1, width2);
        });

        // Determine visible modules (target state)
        List<Module> visibleModules = new ArrayList<>();
        for (Module module : modules) {
            if (module == this || module.getKey() == 0) continue;
            if (inEditor || !module.isEnabled()) {
                visibleModules.add(module);
            }
        }

        // Calculate target dimensions
        String headerText = "Key Bind";
        float headerTextWidth = Skia.getStringWidth(headerText, font);
        float headerIconWidth = Skia.getStringWidth(Icon.KEYBOARD, iconFont);
        float maxContentWidth = headerTextWidth + margin * 4.0f + headerIconWidth;

        for (Module module : visibleModules) {
            String name = module.getName();
            String keyName = getKeyName(module.getKey());
            float nameW = Skia.getStringWidth(name, font);
            float keyW = Skia.getStringWidth(keyName, font);
            float contentW = nameW + margin * 4.0f + keyW;
            if (contentW > maxContentWidth) {
                maxContentWidth = contentW;
            }
        }

        float targetContainerWidth = maxContentWidth + margin * 2.0f;
        float targetContainerHeight = height + (visibleModules.size() * height) + margin * 2.0f;

        // Update container timers
        containerWidthTimer.target = targetContainerWidth;
        containerHeightTimer.target = targetContainerHeight;
        containerWidthTimer.update(true);
        containerHeightTimer.update(true);

        float currentContainerWidth = containerWidthTimer.value;
        float currentContainerHeight = containerHeightTimer.value;

        float startX = dragValue.getX();
        float startY = dragValue.getY();

        // Draw Container Background
        Skia.drawShadow(startX, startY, currentContainerWidth, currentContainerHeight, margin);
        Skia.drawRoundedBlur(startX, startY, currentContainerWidth, currentContainerHeight, margin);
        Skia.drawRoundedRect(startX, startY, currentContainerWidth, currentContainerHeight, margin, BACKGROUND_COLOR);

        // Clip Content
        io.github.humbleui.skija.Canvas canvas = Skia.getCanvas();
        int saveCount = canvas.save();
        io.github.humbleui.types.RRect clipRect = io.github.humbleui.types.RRect.makeXYWH(startX, startY, currentContainerWidth, currentContainerHeight, margin);
        canvas.clipRRect(clipRect, true);

        // Draw Header
        float headerY = startY + margin + height / 2.0f;
        Skia.drawHeightCenteredText(headerText, startX + margin, headerY, Color.WHITE, font);
        Skia.drawHeightCenteredText(Icon.KEYBOARD, startX + currentContainerWidth - margin - headerIconWidth, headerY, Color.WHITE, iconFont);

        // Draw Items (Iterate all potential modules to handle fade out)
        // We iterate all modules in the original list to catch those that are fading out
        // Or better: iterate 'modules' which is all modules sorted.
        // Actually, sorting might change.
        // But for fading out items, their position is what matters.
        
        // Calculate target Y for visible modules
        Map<Module, Float> targetYMap = new HashMap<>();
        float currentTargetYOffset = margin + height; // Start after header
        for (Module module : visibleModules) {
            targetYMap.put(module, currentTargetYOffset);
            currentTargetYOffset += height;
        }

        for (Module module : modules) {
            if (module == this || module.getKey() == 0) continue;

            boolean isVisible = visibleModules.contains(module);
            
            SmoothAnimationTimer yTimer = relativeYTimers.computeIfAbsent(module, k -> new SmoothAnimationTimer(0, 0, 0.2f));
            SmoothAnimationTimer alphaTimer = alphaTimers.computeIfAbsent(module, k -> new SmoothAnimationTimer(0, 0, 0.2f));

            if (isVisible) {
                yTimer.target = targetYMap.get(module);
                alphaTimer.target = 255f;
            } else {
                // If not visible, stay at last position but fade out
                yTimer.target = yTimer.value; 
                alphaTimer.target = 0f;
            }

            yTimer.update(true);
            alphaTimer.update(true);

            float alphaVal = alphaTimer.value;
            if (alphaVal < 1f) continue; // Fully transparent

            float itemYOffset = yTimer.value;
            float itemCenterY = startY + itemYOffset + height / 2.0f;

            Color textColor = new Color(255, 255, 255, (int) Math.min(255, Math.max(0, alphaVal)));

            String name = module.getName();
            String keyName = getKeyName(module.getKey());

            Skia.drawHeightCenteredText(name, startX + margin, itemCenterY, textColor, font);

            float keyW = Skia.getStringWidth(keyName, font);
            Skia.drawHeightCenteredText(keyName, startX + currentContainerWidth - margin - keyW, itemCenterY, textColor, font);
        }

        canvas.restoreToCount(saveCount);

        dragValue.setWidth(currentContainerWidth);
        dragValue.setHeight(currentContainerHeight);
    }

    private void renderStyleA() {
        Font font = Fonts.getMiSans(size.getCurrentValue());
        Font iconFont = Fonts.getIconFill(size.getCurrentValue());
        List<Module> modules = new ArrayList<>(Naven.getInstance().getModuleManager().getModules());
        boolean inEditor = mc.screen instanceof HUDEditor;
        float height = size.getCurrentValue() * 1.5f;
        float margin = size.getCurrentValue() / 3.0f;

        boolean topHalf = dragValue.getY() < mc.getWindow().getGuiScaledHeight() / 2.0f;
        modules.sort((m1, m2) -> {
            float width1 = 0;
            if (m1 != this && m1.getKey() != 0) {
                float nameW = Skia.getStringWidth(m1.getName(), font);
                float keyW = Skia.getStringWidth(getKeyName(m1.getKey()), font) + margin * 2.0f;
                width1 = nameW + margin * 2.0f + margin + keyW;
            }
            
            float width2 = 0;
            if (m2 != this && m2.getKey() != 0) {
                float nameW = Skia.getStringWidth(m2.getName(), font);
                float keyW = Skia.getStringWidth(getKeyName(m2.getKey()), font) + margin * 2.0f;
                width2 = nameW + margin * 2.0f + margin + keyW;
            }
            
            return topHalf ? Float.compare(width2, width1) : Float.compare(width1, width2);
        });

        String headerText = "Key Binds";
        float headerTextWidth = Skia.getStringWidth(headerText, font);
        float headerIconWidth = Skia.getStringWidth(Icon.KEYBOARD, iconFont);
        float headerWidth = headerIconWidth + margin + headerTextWidth + margin * 2.0f;
        float maxWidth = headerWidth;
        for (Module module : modules) {
            if (module == this) {
                continue;
            }
            int key = module.getKey();
            if (key == 0) {
                continue;
            }
            String name = module.getName();
            String keyName = getKeyName(key);
            float nameWidth = Skia.getStringWidth(name, font);
            float keyWidth = Skia.getStringWidth(keyName, font) + margin * 2.0f;
            float width = nameWidth + margin * 2.0f;
            float fullWidth = width + margin + keyWidth;
            if (fullWidth > maxWidth) {
                maxWidth = fullWidth;
            }
        }

        boolean right = dragValue.getX() >= (float) mc.getWindow().getGuiScaledWidth() / 2.0f;
        float startY = dragValue.getY();
        float headerStartX = right ? dragValue.getX() + maxWidth - headerWidth : dragValue.getX();
        Skia.drawShadow(headerStartX, startY, headerWidth, height, margin);
        Skia.drawRoundedBlur(headerStartX, startY, headerWidth, height, margin);
        Skia.drawRoundedRect(headerStartX, startY, headerWidth, height, margin, BACKGROUND_COLOR);
        float headerIconX = headerStartX + margin;
        float headerTextX = headerIconX + headerIconWidth + margin;
        float headerCenterY = startY + height / 2.0f;
        Skia.drawHeightCenteredText(Icon.KEYBOARD, headerIconX, headerCenterY, Color.WHITE, iconFont);
        Skia.drawHeightCenteredText(headerText, headerTextX, headerCenterY, Color.WHITE, font);
        startY += height + margin;
        for (Module module : modules) {
            if (module == this) {
                continue;
            }
            int key = module.getKey();
            if (key == 0) {
                continue;
            }
            String name = module.getName();
            String keyName = getKeyName(key);
            float nameWidth = Skia.getStringWidth(name, font);
            float keyWidth = Skia.getStringWidth(keyName, font) + margin * 2.0f;
            float width = nameWidth + margin * 2.0f;
            float fullWidth = width + margin + keyWidth;
            float targetX = right ? dragValue.getX() + maxWidth - fullWidth : dragValue.getX();
            float offscreenX = right ? dragValue.getX() + maxWidth + fullWidth : dragValue.getX() - fullWidth - margin;

            SmoothAnimationTimer xTimer = xTimers.get(module);
            if (xTimer == null) {
                xTimer = new SmoothAnimationTimer(offscreenX, offscreenX, 0.2F);
                xTimers.put(module, xTimer);
            }
            SmoothAnimationTimer yTimer = yTimers.get(module);
            if (yTimer == null) {
                yTimer = new SmoothAnimationTimer(startY, startY, 0.2F);
                yTimers.put(module, yTimer);
            }
            SmoothAnimationTimer alphaTimer = alphaTimers.get(module);
            if (alphaTimer == null) {
                alphaTimer = new SmoothAnimationTimer(0.0F, 0.0F, 0.25F);
                alphaTimers.put(module, alphaTimer);
            }

            boolean visible = inEditor || !module.isEnabled();
            xTimer.target = visible ? targetX : offscreenX;
            xTimer.update(true);
            alphaTimer.target = visible ? 255.0F : 0.0F;
            alphaTimer.update(true);
            if (visible) {
                yTimer.target = startY;
                yTimer.update(true);
                startY += height + margin;
            } else {
                yTimer.target = yTimer.value;
                yTimer.update(true);
            }

            if (!visible && alphaTimer.isAnimationDone(true) && xTimer.isAnimationDone(true)) {
                continue;
            }

            float alpha = Math.min(1.0f, alphaTimer.value / 255.0f);
            if (alpha <= 0.01f) {
                continue;
            }
            float startX = xTimer.value;
            float renderY = yTimer.value;
            float keyStartX = startX + width + margin;

            if (alpha > 0.1f) {
                Skia.drawShadow(startX, renderY, width, height, margin);
                Skia.drawRoundedBlur(startX, renderY, width, height, margin);
                Skia.drawShadow(keyStartX, renderY, keyWidth, height, margin);
                Skia.drawRoundedBlur(keyStartX, renderY, keyWidth, height, margin);
            }

            Color background = new Color(0, 0, 0, Math.min(255, Math.max(0, (int) (BACKGROUND_COLOR.getAlpha() * alpha))));
            Color textColor = new Color(255, 255, 255, Math.min(255, Math.max(0, (int) (255.0f * alpha))));
            Skia.drawRoundedRect(startX, renderY, width, height, margin, background);
            Skia.drawRoundedRect(keyStartX, renderY, keyWidth, height, margin, background);
            Skia.drawText(name, startX + margin, renderY + margin, textColor, font);
            Skia.drawText(keyName, keyStartX + margin, renderY + margin, textColor, font);
        }

        dragValue.setWidth(maxWidth);
        dragValue.setHeight(startY);
    }

    private String getKeyName(int key) {
        if (key == 0) {
            return "NONE";
        }
        if (key < 0) {
            return "M" + (-key);
        }
        InputConstants.Key inputKey = InputConstants.getKey(key, 0);
        if (inputKey == InputConstants.UNKNOWN) {
            return "NONE";
        }
        String name = inputKey.getDisplayName().getString();
        if (name == null || name.isEmpty()) {
            return "NONE";
        }
        return name.toUpperCase();
    }
}
