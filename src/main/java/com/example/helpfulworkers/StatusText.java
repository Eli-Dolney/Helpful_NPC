package com.example.helpfulworkers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Worker status lines that clients can translate. A status is stored twice: English text for NBT,
 * logic and tests ({@link Worker#status}), and a compact code ({@link Worker#statusCode}) sent to
 * clients, which turn it into a translated {@link Component}.
 *
 * <p>Code format: segments joined by {@link #SEG}; a segment is plain literal text, or {@link #KEY}
 * + translation key followed by args, each prefixed by {@link #ARG}. An arg that starts with
 * {@link #NAME} is a block/item description id and is translated too. Plain strings (old saves,
 * literal statuses) decode as themselves.
 */
final class StatusText {
    private StatusText() {}

    static final char SEG = '\u001e';
    static final char KEY = '\u001f';
    static final char ARG = '\u001d';
    static final char NAME = '\u001c';

    /** A block or item name argument, translated on the client (and to English on the server). */
    record Name(String descriptionId) {}

    static Name name(Block block) { return new Name(block.getDescriptionId()); }
    static Name name(Item item) { return new Name(item.getDescriptionId()); }
    static Name name(ItemStack stack) { return new Name(stack.getItem().getDescriptionId()); }

    static String encode(String key, Object... args) {
        StringBuilder out = new StringBuilder().append(KEY).append(key);
        for (Object arg : args) {
            out.append(ARG);
            if (arg instanceof Name n) out.append(NAME).append(n.descriptionId());
            else out.append(clean(String.valueOf(arg)));
        }
        return out.toString();
    }

    /** A literal piece of text, with any control characters that would confuse decoding removed. */
    static String literal(String text) {
        return text == null ? "" : clean(text);
    }

    static String join(String first, String second) {
        if (second == null || second.isEmpty()) return first;
        if (first == null || first.isEmpty()) return second;
        return first + SEG + second;
    }

    private static String clean(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != SEG && c != KEY && c != ARG && c != NAME) b.append(c);
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- decoding

    /** Client side: the translated status. */
    static Component toComponent(String code) {
        if (code == null || code.isEmpty()) return Component.empty();
        MutableComponent out = Component.empty();
        for (String segment : code.split(String.valueOf(SEG), -1)) {
            if (segment.isEmpty() || segment.charAt(0) != KEY) { out.append(Component.literal(segment)); continue; }
            String[] parts = segment.substring(1).split(String.valueOf(ARG), -1);
            Object[] args = new Object[parts.length - 1];
            for (int i = 1; i < parts.length; i++) {
                String a = parts[i];
                args[i - 1] = !a.isEmpty() && a.charAt(0) == NAME ? Component.translatable(a.substring(1)) : a;
            }
            out.append(Component.translatable(parts[0], args));
        }
        return out;
    }

    /** Server side: the same status in English, from this mod's en_us.json and vanilla's names. */
    static String english(String code) {
        if (code == null || code.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String segment : code.split(String.valueOf(SEG), -1)) {
            if (segment.isEmpty() || segment.charAt(0) != KEY) { out.append(segment); continue; }
            String[] parts = segment.substring(1).split(String.valueOf(ARG), -1);
            Object[] args = new Object[parts.length - 1];
            for (int i = 1; i < parts.length; i++) {
                String a = parts[i];
                args[i - 1] = !a.isEmpty() && a.charAt(0) == NAME ? Language.getInstance().getOrDefault(a.substring(1)) : a;
            }
            String pattern = englishTable().getOrDefault(parts[0], parts[0]);
            try { out.append(String.format(pattern, args)); }
            catch (java.util.IllegalFormatException ex) { out.append(pattern); }
        }
        return out.toString();
    }

    /** The overhead line: translated, then cut to {@link Worker#STATUS_LINE_MAX} characters. */
    static String overheadLine(String code) {
        String text = toComponent(code).getString();
        return text.length() <= Worker.STATUS_LINE_MAX ? text : text.substring(0, Worker.STATUS_LINE_MAX - 1) + "…";
    }

    /** Keys used by a code, for tests. */
    static List<String> keys(String code) {
        List<String> keys = new ArrayList<>();
        if (code == null) return keys;
        for (String segment : code.split(String.valueOf(SEG), -1)) {
            if (!segment.isEmpty() && segment.charAt(0) == KEY) keys.add(segment.substring(1).split(String.valueOf(ARG), -1)[0]);
        }
        return keys;
    }

    private static Map<String, String> english;

    static synchronized Map<String, String> englishTable() {
        if (english != null) return english;
        Map<String, String> table = new HashMap<>();
        try (InputStream in = StatusText.class.getResourceAsStream("/assets/" + HelpfulWorkers.ID + "/lang/en_us.json")) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : json.entrySet()) table.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception ex) {
            HelpfulWorkers.LOGGER.warn("Could not read en_us.json for status text", ex);
        }
        english = table;
        return english;
    }
}
