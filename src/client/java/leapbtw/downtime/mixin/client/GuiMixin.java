package leapbtw.downtime.mixin.client;

import leapbtw.downtime.client.DowntimeClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GuiRenderState guiRenderState;

    // This GUI pass is shared by the HUD, loading screens and menus.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void downtime$showStopwatch(DeltaTracker delta, boolean renderHud, boolean renderScreen, CallbackInfo ci) {
        long elapsedSeconds = DowntimeClient.breakElapsedSeconds();
        if (elapsedSeconds < 0) return;

        long awaySeconds = DowntimeClient.breakAwaySeconds();
        String minimum = DowntimeClient.formatBreakTime(DowntimeClient.BREAK_MINIMUM_SECONDS);
        Component stopwatch = Component.translatable("gui.downtime.break_stopwatch",
                DowntimeClient.formatBreakTime(elapsedSeconds));
        Component hint;
        if (awaySeconds < 0) {
            hint = Component.translatable("gui.downtime.break_leave", minimum);
        } else if (awaySeconds < DowntimeClient.BREAK_MINIMUM_SECONDS) {
            hint = Component.translatable("gui.downtime.break_away", DowntimeClient.formatBreakTime(awaySeconds), minimum);
        } else {
            hint = Component.translatable("gui.downtime.break_ready");
        }

        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, guiRenderState, 0, 0);
        int center = graphics.guiWidth() / 2;
        int halfWidth = (Math.max(minecraft.font.width(stopwatch), minecraft.font.width(hint)) + 1) / 2 + 6;
        graphics.nextStratum();
        graphics.fill(center - halfWidth, 5, center + halfWidth, 32, 0xDD101010);
        graphics.centeredText(minecraft.font, stopwatch, center, 9, 0xFFFFFFFF);
        graphics.centeredText(minecraft.font, hint, center, 21, 0xFFDDDDDD);
    }
}
