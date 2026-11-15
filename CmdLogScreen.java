package pl.cmdlog.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import pl.cmdlog.QueryPayload;
import pl.cmdlog.ResultPayload;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CmdLogScreen extends Screen {
    private static final int LINE_H = 11;
    private static final int TOP = 92;

    private TextFieldWidget nickField;
    private TextFieldWidget dateField;
    private TextFieldWidget filterField;

    private String lastNick = "";
    private String lastDate = LocalDate.now().toString();
    private String lastFilter = "";

    private String status = "Wpisz nick (puste = wszyscy) i date, potem Szukaj.";
    private int statusColor = 0xFFAAAAAA;

    private List<String> allLines = List.of();
    private List<String> lines = List.of();
    private String resultNick = "";
    private String resultDate = "";
    private boolean hasResult = false;
    private int scroll = 0;

    public CmdLogScreen() {
        super(Text.literal("CmdLog - komendy graczy"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        nickField = new TextFieldWidget(this.textRenderer, cx - 150, 28, 130, 20, Text.literal("Nick"));
        nickField.setMaxLength(16);
        nickField.setPlaceholder(Text.literal("Nick (puste=wszyscy)"));
        nickField.setText(lastNick);

        dateField = new TextFieldWidget(this.textRenderer, cx - 12, 28, 90, 20, Text.literal("Data"));
        dateField.setMaxLength(10);
        dateField.setText(lastDate);

        // lupa - filtruje wyniki na zywo (po komendzie lub nicku)
        filterField = new TextFieldWidget(this.textRenderer, cx - 150, 52, 300, 20, Text.literal("Lupa"));
        filterField.setMaxLength(100);
        filterField.setPlaceholder(Text.literal("Lupa: wpisz fragment komendy, np. /tp"));
        filterField.setText(lastFilter);
        filterField.setChangedListener(s -> {
            lastFilter = s;
            applyFilter();
        });

        addDrawableChild(nickField);
        addDrawableChild(dateField);
        addDrawableChild(filterField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Szukaj"), b -> search())
                .dimensions(cx + 84, 28, 66, 20).build());

        setInitialFocus(nickField);
    }

    private void search() {
        lastNick = nickField.getText().trim();
        lastDate = dateField.getText().trim();

        try {
            LocalDate.parse(lastDate);
        } catch (DateTimeParseException e) {
            setStatus("Zly format daty - uzyj RRRR-MM-DD.", 0xFFFF5555);
            return;
        }

        setStatus("Szukam...", 0xFFAAAAAA);
        ClientPlayNetworking.send(new QueryPayload(lastNick, lastDate));
    }

    public void onResult(ResultPayload p) {
        if (!p.error().isEmpty()) {
            allLines = List.of();
            lines = List.of();
            hasResult = false;
            setStatus(p.error(), 0xFFFF5555);
            return;
        }
        allLines = p.lines();
        resultNick = p.nick();
        resultDate = p.date();
        hasResult = true;
        applyFilter();
    }

    private void applyFilter() {
        String f = lastFilter.trim().toLowerCase(Locale.ROOT);
        if (f.isEmpty()) {
            lines = allLines;
        } else {
            List<String> out = new ArrayList<>();
            for (String l : allLines) {
                if (l.toLowerCase(Locale.ROOT).contains(f)) out.add(l);
            }
            lines = out;
        }
        scroll = 0;

        if (!hasResult) return;
        String who = resultNick.isEmpty() ? "wszyscy gracze" : resultNick;
        if (allLines.isEmpty()) {
            setStatus("Brak komend: " + who + ", dzien " + resultDate + ".", 0xFFFFFF55);
        } else if (lines.isEmpty()) {
            setStatus("Lupa: brak wynikow (" + allLines.size() + " komend tego dnia).", 0xFFFFFF55);
        } else if (f.isEmpty()) {
            setStatus(who + " - " + resultDate + ": " + lines.size() + " komend(y)", 0xFF55FF55);
        } else {
            setStatus(who + " - " + resultDate + ": " + lines.size() + " z " + allLines.size() + " komend", 0xFF55FF55);
        }
    }

    private void setStatus(String text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    private int visibleLines() {
        return Math.max(1, (this.height - 10 - TOP) / LINE_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, lines.size() - visibleLines());
        scroll -= (int) Math.signum(verticalAmount) * 3;
        scroll = Math.max(0, Math.min(scroll, max));
        return true;
    }

    @Override
    public boolean shouldPause() {
        // Bez tego w singleplayerze serwer by sie zatrzymal i nie odpowiedzial.
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(status), cx - 150, 77, statusColor);

        int left = cx - 150;
        int right = cx + 150;
        int bottom = this.height - 8;
        ctx.fill(left - 4, TOP - 3, right + 4, bottom + 2, 0xA0000000);

        int visible = visibleLines();
        int max = Math.max(0, lines.size() - visible);
        scroll = Math.max(0, Math.min(scroll, max));

        int y = TOP;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            String trimmed = this.textRenderer.trimToWidth(lines.get(i), right - left);
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(trimmed), left, y, 0xFFFFFFFF);
            y += LINE_H;
        }

        if (lines.size() > visible) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal((scroll + 1) + "-" + Math.min(lines.size(), scroll + visible) + " / " + lines.size()),
                    right - 70, 10, 0xFFAAAAAA);
        }
    }
}
