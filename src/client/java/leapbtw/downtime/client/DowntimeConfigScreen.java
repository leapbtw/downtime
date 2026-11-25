package leapbtw.downtime.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

public final class DowntimeConfigScreen extends Screen {
    private static final Component START_LABEL = Component.translatable("gui.downtime.sepia_start");
    private static final Component END_LABEL = Component.translatable("gui.downtime.sepia_end");
    private static final int COLUMN_GAP = 8;

    private final Screen parent;
    private final SepiaSchedule savedSchedule = DowntimeClient.config.sepiaSchedule();
    private int minutes = DowntimeClient.config.minutes();
    private boolean sepiaEnabled = savedSchedule.enabled();
    private String startDraft = SepiaSchedule.formatTime(savedSchedule.start());
    private String endDraft = SepiaSchedule.formatTime(savedSchedule.end());
    private Component saveError;
    private EditBox startField;
    private EditBox endField;

    public DowntimeConfigScreen(Screen parent) {
        super(Component.translatable("gui.downtime.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int widgetWidth = formWidth();
        int left = (width - widgetWidth) / 2;
        int top = formTop();
        int columnWidth = (widgetWidth - COLUMN_GAP) / 2;
        int secondColumn = left + columnWidth + COLUMN_GAP;

        addRenderableWidget(new AbstractSliderButton(left, top + 17, widgetWidth, 20,
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
        addRenderableWidget(Checkbox.builder(Component.translatable("gui.downtime.sepia_schedule"), font)
                .pos(left, top + 73).maxWidth(widgetWidth).selected(sepiaEnabled)
                .onValueChange((_, checked) -> {
                    sepiaEnabled = checked;
                    saveError = null;
                    updateTimeFields();
                }).build());
        startField = addTimeField(START_LABEL, startDraft, left, top + 106, columnWidth, value -> startDraft = value);
        endField = addTimeField(END_LABEL, endDraft, secondColumn, top + 106, columnWidth, value -> endDraft = value);
        updateTimeFields();

        addRenderableWidget(Button.builder(Component.translatable("gui.downtime.save"), _ -> save())
                .bounds(left, top + 147, columnWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, _ -> onClose())
                .bounds(secondColumn, top + 147, columnWidth, 20).build());
    }

    private int formWidth() { return Math.min(300, width - 30); }

    private int formTop() { return Math.max(4, (height - 167) / 2); }

    private EditBox addTimeField(Component label, String initial, int x, int y, int width, Consumer<String> edit) {
        EditBox field = new EditBox(font, x, y, width, 20, label);
        field.setMaxLength(16);
        field.setValue(initial);
        field.setResponder(value -> {
            edit.accept(value);
            saveError = null;
        });
        return addRenderableWidget(field);
    }

    private void updateTimeFields() {
        startField.setEditable(sepiaEnabled);
        endField.setEditable(sepiaEnabled);
        startField.active = sepiaEnabled;
        endField.active = sepiaEnabled;
    }

    private void save() {
        SepiaSchedule schedule = new SepiaSchedule(false, savedSchedule.start(), savedSchedule.end());
        Component invalidSchedule = null;
        try {
            schedule = new SepiaSchedule(sepiaEnabled, SepiaSchedule.parseTime(startDraft), SepiaSchedule.parseTime(endDraft));
        } catch (DateTimeParseException exception) {
            invalidSchedule = Component.translatable("gui.downtime.sepia_invalid");
        } catch (IllegalArgumentException exception) {
            invalidSchedule = Component.translatable("gui.downtime.sepia_identical");
        }
        if (sepiaEnabled && invalidSchedule != null) {
            saveError = invalidSchedule;
            return;
        }
        if (DowntimeClient.config.save(minutes, schedule)) onClose();
        else saveError = Component.translatable("gui.downtime.save_failed");
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int top = formTop();
        graphics.centeredText(font, title, width / 2, top, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("gui.downtime.range"), width / 2, top + 41, 0xFFCCCCCC);
        graphics.centeredText(font, Component.translatable("gui.downtime.warning"), width / 2, top + 51, 0xFFCCCCCC);
        graphics.centeredText(font, Component.translatable("gui.downtime.next_cycle"), width / 2, top + 61, 0xFFCCCCCC);
        int labelColor = sepiaEnabled ? 0xFFFFFFFF : 0xFFAAAAAA;
        graphics.text(font, START_LABEL, startField.getX(), top + 95, labelColor);
        graphics.text(font, END_LABEL, endField.getX(), top + 95, labelColor);
        Component hint = saveError == null ? Component.translatable("gui.downtime.sepia_example") : saveError;
        graphics.centeredText(font, hint, width / 2, top + 132, saveError == null ? 0xFFCCCCCC : 0xFFFF7777);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
