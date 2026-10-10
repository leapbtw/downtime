package leapbtw.downtime.mixin.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import leapbtw.downtime.client.DowntimeClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique private static final Identifier DOWNTIME_GRAYSCALE = Identifier.fromNamespaceAndPath("downtime", "grayscale");
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    // Run after both world and GUI rendering, before the shared allocator ends its frame.
    // Do not replace the vanilla spectator effect or retain chains across resource reloads.
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"))
    private void downtime$grayscale(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        if (!DowntimeClient.grayscale()) return;
        PostChain chain = minecraft.getShaderManager().getPostChain(DOWNTIME_GRAYSCALE, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) return;
        FrameGraphBuilder frame = new FrameGraphBuilder();
        PostChain.TargetBundle targets = PostChain.TargetBundle.of(PostChain.MAIN_TARGET_ID,
                frame.importExternal("downtime_main", mainRenderTarget));
        chain.addToFrame(frame, mainRenderTarget.width, mainRenderTarget.height, targets);
        frame.execute(resourcePool);
    }
}
