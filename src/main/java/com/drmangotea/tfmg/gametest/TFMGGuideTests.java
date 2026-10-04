package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Checks The Factory Handbook, a Patchouli book read here straight from the
 * mod file: the book definition exists, English and French have the same
 * entries, every entry points at an existing category, and every item and
 * block state a page names exists. A typo would otherwise only show up as a
 * missing icon or an empty multiblock in someone's game.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGGuideTests {

    private static final String[] LANGS = {"en_us", "fr_fr"};

    @GameTest(template = "gametest/platform", batch = "tfmg_data")
    public static void handbookIsValid(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var file = ModList.get().getModFileById(TFMG.MOD_ID).getFile();
        if (!Files.exists(file.findResource("data", TFMG.MOD_ID, "patchouli_books", "handbook", "book.json")))
            problems.add("data/tfmg/patchouli_books/handbook/book.json is missing");

        Set<String> referenceEntries = null;
        for (String lang : LANGS) {
            Path root = file.findResource("assets", TFMG.MOD_ID, "patchouli_books", "handbook", lang);
            Set<String> categories = new TreeSet<>();
            Set<String> entries = new TreeSet<>();
            try (Stream<Path> files = Files.walk(root.resolve("categories"))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String id = f.getFileName().toString().replace(".json", "");
                    categories.add(TFMG.MOD_ID + ":" + id);
                    JsonObject json = read(f, problems);
                    if (json != null)
                        checkItem(lang + " category " + id + " icon", json.get("icon"), problems);
                }
            } catch (Exception e) {
                problems.add(lang + ": cannot list categories (" + e.getMessage() + ")");
                continue;
            }
            Path entryRoot = root.resolve("entries");
            try (Stream<Path> files = Files.walk(entryRoot)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    String id = entryRoot.relativize(f).toString().replace('\\', '/');
                    entries.add(id);
                    JsonObject json = read(f, problems);
                    if (json != null)
                        checkEntry(lang + " " + id, json, categories, problems);
                }
            } catch (Exception e) {
                problems.add(lang + ": cannot list entries (" + e.getMessage() + ")");
            }
            if (entries.isEmpty())
                problems.add(lang + ": no entries");
            if (referenceEntries == null)
                referenceEntries = entries;
            else if (!referenceEntries.equals(entries))
                problems.add(lang + ": entries differ from " + LANGS[0]);
        }
        if (!problems.isEmpty()) {
            TFMG.LOGGER.error("[gametest] handbook problems: {}", problems);
            helper.fail(problems.size() + " handbook problems: " + problems.subList(0, Math.min(30, problems.size())));
        }
        helper.succeed();
    }

    private static void checkEntry(String where, JsonObject entry, Set<String> categories, List<String> problems) {
        if (!entry.has("category") || !categories.contains(entry.get("category").getAsString()))
            problems.add(where + ": unknown category " + entry.get("category"));
        checkItem(where + " icon", entry.get("icon"), problems);
        if (!entry.has("pages") || entry.getAsJsonArray("pages").isEmpty()) {
            problems.add(where + ": no pages");
            return;
        }
        int index = 0;
        for (JsonElement element : entry.getAsJsonArray("pages")) {
            index++;
            JsonObject page = element.getAsJsonObject();
            String type = page.has("type") ? page.get("type").getAsString() : "";
            String at = where + " page " + index;
            switch (type) {
                case "patchouli:text" -> {
                    if (!page.has("text") || page.get("text").getAsString().isBlank())
                        problems.add(at + ": empty text");
                }
                case "patchouli:spotlight" -> checkItem(at, page.get("item"), problems);
                case "patchouli:multiblock" -> checkMultiblock(at, page.getAsJsonObject("multiblock"), problems);
                default -> problems.add(at + ": unexpected page type '" + type + "'");
            }
        }
    }

    private static void checkMultiblock(String where, JsonObject multiblock, List<String> problems) {
        JsonObject mapping = multiblock.getAsJsonObject("mapping");
        for (String symbol : mapping.keySet()) {
            String spec = mapping.get(symbol).getAsString();
            try {
                BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), spec, false);
            } catch (Exception e) {
                problems.add(where + ": bad block state '" + spec + "'");
            }
        }
        int zeros = 0;
        int width = -1, depth = -1;
        for (JsonElement layer : multiblock.getAsJsonArray("pattern")) {
            if (depth < 0)
                depth = layer.getAsJsonArray().size();
            else if (depth != layer.getAsJsonArray().size())
                problems.add(where + ": layers of different depth");
            for (JsonElement row : layer.getAsJsonArray()) {
                String r = row.getAsString();
                if (width < 0)
                    width = r.length();
                else if (width != r.length())
                    problems.add(where + ": rows of different width");
                for (char c : r.toCharArray()) {
                    if (c == '0')
                        zeros++;
                    else if (c != ' ' && c != '_' && !mapping.has(String.valueOf(c)))
                        problems.add(where + ": symbol '" + c + "' missing from the mapping");
                }
            }
        }
        if (zeros != 1)
            problems.add(where + ": the pattern needs exactly one '0', found " + zeros);
    }

    private static JsonObject read(Path f, List<String> problems) {
        try (Reader reader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            problems.add(f.getFileName() + ": unreadable (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void checkItem(String where, JsonElement element, List<String> problems) {
        if (element == null) {
            problems.add(where + ": missing item");
            return;
        }
        for (String id : element.getAsString().split(",")) {
            ResourceLocation location = ResourceLocation.tryParse(id.trim());
            if (location == null || !BuiltInRegistries.ITEM.containsKey(location))
                problems.add(where + ": unknown item '" + id + "'");
        }
    }
}
