package leapbtw.downtime.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class DowntimeConfigScreen extends Screen {
    private final Screen parent;
    private int minutes = DowntimeClient.config.minutes();
    private boolean saveFailed;

    public DowntimeConfigScreen(Screen parent) {
        super(Component.translatable("gui.downtime.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int widgetWidth = Math.min(300, width - 30);
        int left = (width - widgetWidth) / 2;
        int top = height / 2 - 20;
        addRenderableWidget(new AbstractSliderButton(left, top, widgetWidth, 20,
                Component.empty(), (minutes - 30) / 210.0) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.translatable("gui.downtime.interval", minutes));
            }
            @Override protected void applyValue() {
                minutes = 30 + (int) Math.round(value * 14) * 15;
            }
            @Override protected void setValue(double value) {
                super.setValue(Math.round(Math.clamp(value, 0, 1) * 14) / 14.0);
            }
            @Override public boolean keyPressed(net.minecraft.client.input. @NonNull KeyEvent event) {
                if (canChangeValue && (event.key() == 263 || event.key() == 262)) {
                    setValue(value + (event.key() == 263 ? -1 : 1) / 14.0);
                    return true;
                }
                return super.keyPressed(event);
            }
        });
        addRenderableWidget(Button.builder(Component.translatable("gui.downtime.save"), _ -> {
            if (DowntimeClient.config.save(minutes)) onClose();
            else saveFailed = true;
        }).bounds(left, top + 75, (widgetWidth - 8) / 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, _ -> onClose())
                .bounds(left + (widgetWidth + 8) / 2, top + 75, (widgetWidth - 8) / 2, 20).build());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int top = height / 2 - 20;
        graphics.centeredText(font, title, width / 2, top - 40, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.downtime.range"), width / 2, top + 28, 0xFFCCCCCC);
        graphics.centeredText(font, Component.translatable("gui.downtime.warning"), width / 2, top + 42, 0xFFCCCCCC);
        graphics.centeredText(font, Component.translatable("gui.downtime.next_cycle"), width / 2, top + 56, 0xFFCCCCCC);
        if (saveFailed) graphics.centeredText(font, Component.translatable("gui.downtime.save_failed"), width / 2, top + 105, 0xFFFF7777);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
