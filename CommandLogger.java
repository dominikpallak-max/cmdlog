package pl.cmdlog;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Zapis i odczyt logow komend. Jeden plik na dzien: cmdlog/logs/RRRR-MM-DD.log */
public final class CommandLogger {
    public static final int MAX_RESULTS = 500;
    private static final int MAX_LINE = 250;

    private static final Path BASE_DIR = FabricLoader.getInstance().getGameDir().resolve("cmdlog");
    private static final Path LOG_DIR = BASE_DIR.resolve("logs");
    private static final Path ADMINS = BASE_DIR.resolve("admins.txt");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private CommandLogger() {}

    public static synchronized void log(String nick, UUID uuid, String command) {
        try {
            Files.createDirectories(LOG_DIR);
            LocalDateTime now = LocalDateTime.now();
            String line = TIME.format(now) + "\t" + uuid + "\t" + nick + "\t" + clean(command) + System.lineSeparator();
            Files.writeString(LOG_DIR.resolve(now.toLocalDate() + ".log"), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            CmdLogMod.LOGGER.error("Nie udalo sie zapisac komendy", e);
        }
    }

    /** Zwraca komendy gracza z danego dnia (najstarsze pierwsze), maks. MAX_RESULTS ostatnich. */
    public static synchronized List<String> query(String nick, LocalDate date) {
        Path file = LOG_DIR.resolve(date + ".log");
        List<String> out = new ArrayList<>();
        if (!Files.exists(file)) return out;

        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.forEach(l -> {
                String[] p = l.split("\t", 4);
                if (p.length == 4 && p[2].equalsIgnoreCase(nick)) {
                    String entry = p[0] + "  /" + p[3];
                    out.add(entry.length() > MAX_LINE ? entry.substring(0, MAX_LINE) : entry);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            CmdLogMod.LOGGER.error("Nie udalo sie odczytac logu", e);
        }

        if (out.size() > MAX_RESULTS) {
            return new ArrayList<>(out.subList(out.size() - MAX_RESULTS, out.size()));
        }
        return out;
    }

    /** Admini to nicki wpisane w cmdlog/admins.txt (jeden na linie, # = komentarz). */
    public static boolean isAdmin(String nick) {
        try {
            if (!Files.exists(ADMINS)) return false;
            for (String l : Files.readAllLines(ADMINS, StandardCharsets.UTF_8)) {
                String t = l.trim();
                if (!t.isEmpty() && !t.startsWith("#") && t.equalsIgnoreCase(nick)) return true;
            }
        } catch (IOException e) {
            CmdLogMod.LOGGER.error("Nie udalo sie odczytac admins.txt", e);
        }
        return false;
    }

    public static void ensureFiles() {
        try {
            Files.createDirectories(LOG_DIR);
            if (!Files.exists(ADMINS)) {
                Files.writeString(ADMINS,
                        "# Nicki graczy, ktorzy moga uzywac menu /cmdlog - jeden nick na linie\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            CmdLogMod.LOGGER.error("Nie udalo sie utworzyc plikow moda", e);
        }
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
