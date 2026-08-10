package io.github.darkkronicle.advancedchathud.mixin;

import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 split InGameHud into Gui (screens) and Hud (the HUD itself), and renamed the
 * render pass to extractRenderState. Hooking its RETURN is the 26.2 equivalent of the
 * old "just after the subtitles were drawn" injection point.
 */
@Mixin(value = Hud.class, priority = 1150)
public abstract class MixinInGameHud {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void afterRender(
            GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo info) {
        ((RenderEventHandler) RenderEventHandler.getInstance())
                .runExtractGuiOverlayPost(
                        GuiContext.fromGuiGraphics(context),
                        tickCounter.getGameTimeDeltaPartialTick(false));
    }
}
