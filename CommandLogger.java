package pl.cmdlog;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Zapis i odczyt komend graczy.
 * Zrodla przy wyszukiwaniu:
 *  1) wlasny log moda: cmdlog/logs/RRRR-MM-DD.log
 *  2) logi konsoli serwera: logs/latest.log (dzis) i logs/RRRR-MM-DD-N.log(.gz) - wpisy "issued server command"
 * Dzieki 2) widac tez komendy sprzed instalacji moda.
 */
public final class CommandLogger {
    public static final int MAX_RESULTS = 2000;
    private static final int MAX_LINE = 250;

    private static final Path BASE_DIR = FabricLoader.getInstance().getGameDir().resolve("cmdlog");
    private static final Path LOG_DIR = BASE_DIR.resolve("logs");
    private static final Path ADMINS = BASE_DIR.resolve("admins.txt");
    private static final Path SERVER_LOGS = FabricLoader.getInstance().getGameDir().resolve("logs");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // [12:34:56] [Server thread/INFO]: Nick issued server command: /cmd
    private static final Pattern VANILLA = Pattern.compile(
            "^\\[(\\d{2}:\\d{2}:\\d{2})] \\[[^\\]]*/INFO\\]: (\\S+) issued server command: (.*)$");

    private CommandLogger() {}

    private record Entry(String time, String nick, String cmd) {}

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

    /**
     * Komendy z danego dnia, najstarsze pierwsze. Pusty nick = wszyscy gracze.
     * Gracz nie musi byc online - szukamy w zapisanych logach.
     */
    public static synchronized List<String> query(String nick, LocalDate date) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        readOwnLog(date, nick, entries, seen);
        readServerLogs(date, nick, entries, seen);

        entries.sort((a, b) -> a.time().compareTo(b.time()));

        List<String> out = new ArrayList<>(entries.size());
        boolean all = nick.isEmpty();
        for (Entry e : entries) {
            String s = all ? e.time() + "  " + e.nick() + ": /" + e.cmd() : e.time() + "  /" + e.cmd();
            out.add(s.length() > MAX_LINE ? s.substring(0, MAX_LINE) : s);
        }
        if (out.size() > MAX_RESULTS) {
            return new ArrayList<>(out.subList(out.size() - MAX_RESULTS, out.size()));
        }
        return out;
    }

    private static void readOwnLog(LocalDate date, String nick, List<Entry> entries, Set<String> seen) {
        Path file = LOG_DIR.resolve(date + ".log");
        if (!Files.exists(file)) return;
        try (BufferedReader r = reader(file)) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split("\t", 4);
                if (p.length == 4) add(entries, seen, nick, p[0], p[2], p[3]);
            }
        } catch (IOException | UncheckedIOException e) {
            CmdLogMod.LOGGER.error("Nie udalo sie odczytac logu moda", e);
        }
    }

    private static void readServerLogs(LocalDate date, String nick, List<Entry> entries, Set<String> seen) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(SERVER_LOGS)) return;

        if (date.equals(LocalDate.now())) {
            Path latest = SERVER_LOGS.resolve("latest.log");
            if (Files.exists(latest)) files.add(latest);
        }
        String prefix = date + "-";
        try (Stream<Path> s = Files.list(SERVER_LOGS)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && (n.endsWith(".log") || n.endsWith(".log.gz"));
            }).forEach(files::add);
        } catch (IOException e) {
            CmdLogMod.LOGGER.error("Nie udalo sie przejrzec folderu logs", e);
        }

        for (Path f : files) {
            try (BufferedReader r = reader(f)) {
                String l;
                while ((l = r.readLine()) != null) {
                    Matcher m = VANILLA.matcher(l);
                    if (m.matches()) add(entries, seen, nick, m.group(1), m.group(2), m.group(3));
                }
            } catch (IOException | UncheckedIOException e) {
                CmdLogMod.LOGGER.warn("Pominieto log {}: {}", f.getFileName(), e.toString());
            }
        }
    }

    private static void add(List<Entry> entries, Set<String> seen, String nickFilter,
                            String time, String nick, String cmd) {
        if (!nickFilter.isEmpty() && !nick.equalsIgnoreCase(nickFilter)) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        // ta sama komenda moze byc i w logu moda, i w logu serwera - bierzemy raz
        String key = time + "|" + nick.toLowerCase(Locale.ROOT) + "|" + c;
        if (seen.add(key)) entries.add(new Entry(time, nick, c));
    }

    private static BufferedReader reader(Path p) throws IOException {
        var in = Files.newInputStream(p);
        if (p.getFileName().toString().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
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
