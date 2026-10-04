package com.drmangotea.tfmg.content.items.blueprint.client;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.content.items.blueprint.FactoryBlueprintItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.IStateMatcher;
import vazkii.patchouli.api.PatchouliAPI;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side of the Factory Blueprint: loads the structures from
 * {@code assets/tfmg/blueprints/*.json} (generated from the handbook's
 * schematics), and shows them in the world one layer at a time through
 * Patchouli's multiblock visualizer. A layer that the world now matches is
 * replaced by the next one on the following client tick.
 *
 * @author vyrriox
 */
@OnlyIn(Dist.CLIENT)
public final class BlueprintProjector implements FactoryBlueprintItem.ClientHandler {

    public static final BlueprintProjector INSTANCE = new BlueprintProjector();

    private record Blueprint(String id, String line, int order, Map<String, String> name, List<Map<BlockPos, BlockState>> layers,
                             int sizeX, int sizeZ) {
    }

    private List<Blueprint> blueprints;
    // The line of the blueprint item in use, and the structures it covers.
    private String line;
    private List<Blueprint> inLine = List.of();
    private int selected;

    // Active projection; layer < 0 when nothing is projected.
    private int layer = -1;
    private BlockPos anchor;
    private Rotation rotation = Rotation.NONE;
    private IMultiblock shown;

    private BlueprintProjector() {
    }

    // ------------------------------------------------------------ input

    @Override
    public void useInAir(Player player, String line, boolean sneaking) {
        List<Blueprint> all = selectLine(line);
        if (all.isEmpty()) {
            say(player, Component.translatable("tfmg.blueprint.none"));
            return;
        }
        if (layer >= 0) {
            int target = layer + (sneaking ? -1 : 1);
            Blueprint b = all.get(selected);
            if (target < 0)
                target = 0;
            if (target >= b.layers().size()) {
                say(player, Component.translatable("tfmg.blueprint.done", name(b)).withStyle(ChatFormatting.GREEN));
                clear();
                return;
            }
            showLayer(player, target);
            return;
        }
        selected = Math.floorMod(selected + (sneaking ? -1 : 1), all.size());
        Blueprint b = all.get(selected);
        say(player, Component.translatable("tfmg.blueprint.selected", name(b), selected + 1, all.size(), b.layers().size())
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void useOnBlock(Player player, String line, BlockPos pos, Direction face, boolean sneaking) {
        if (sneaking) {
            clear();
            say(player, Component.translatable("tfmg.blueprint.cleared"));
            return;
        }
        List<Blueprint> all = selectLine(line);
        if (all.isEmpty()) {
            say(player, Component.translatable("tfmg.blueprint.none"));
            return;
        }
        anchor = pos.relative(face);
        rotation = rotationFor(player.getDirection());
        showLayer(player, 0);
    }

    // ------------------------------------------------------------ tick

    /** Called every client tick: moves on once the shown layer is built. */
    public void tick() {
        if (layer < 0 || shown == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;
        if (PatchouliAPI.get().getCurrentMultiblock() != shown && !shown.validate(mc.level, anchor, rotation)) {
            // The player closed the projection with Patchouli's own control.
            layer = -1;
            shown = null;
            return;
        }
        if (!shown.validate(mc.level, anchor, rotation))
            return;
        Blueprint b = inLine.get(selected);
        if (layer + 1 >= b.layers().size()) {
            say(mc.player, Component.translatable("tfmg.blueprint.done", name(b)).withStyle(ChatFormatting.GREEN));
            clear();
        } else {
            showLayer(mc.player, layer + 1);
        }
    }

    // ------------------------------------------------------------ helpers

    private void showLayer(Player player, int index) {
        Blueprint b = inLine.get(selected);
        layer = index;
        Map<BlockPos, IStateMatcher> positions = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : b.layers().get(index).entrySet())
            positions.put(e.getKey(), PatchouliAPI.get().strictBlockMatcher(e.getValue().getBlock()));
        shown = PatchouliAPI.get().makeSparseMultiblock(positions);
        Component title = Component.translatable("tfmg.blueprint.layer", name(b), index + 1, b.layers().size());
        PatchouliAPI.get().showMultiblock(shown, title, anchor, rotation);
        say(player, title.copy().withStyle(ChatFormatting.GOLD));
    }

    private void clear() {
        if (shown != null && PatchouliAPI.get().getCurrentMultiblock() == shown)
            PatchouliAPI.get().clearMultiblock();
        layer = -1;
        shown = null;
    }

    /** Structures are drawn facing south; turn them to face the player. */
    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static Component name(Blueprint b) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return Component.literal(b.name().getOrDefault(lang, b.name().getOrDefault("en_us", b.id())));
    }

    private static void say(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** Switches to the structures of a line; a different line drops the projection. */
    private List<Blueprint> selectLine(String newLine) {
        if (!newLine.equals(line)) {
            clear();
            line = newLine;
            inLine = blueprints().stream().filter(b -> newLine.equals(b.line())).toList();
            selected = 0;
        }
        return inLine;
    }

    private List<Blueprint> blueprints() {
        if (blueprints == null)
            blueprints = load();
        return blueprints;
    }

    /** Re-read on resource reload so edited blueprints show up without a restart. */
    public void invalidate() {
        blueprints = null;
    }

    private static List<Blueprint> load() {
        List<Blueprint> out = new ArrayList<>();
        Map<ResourceLocation, Resource> found = Minecraft.getInstance().getResourceManager()
                .listResources("blueprints", id -> id.getNamespace().equals(TFMG.MOD_ID) && id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                out.add(parse(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                TFMG.LOGGER.error("Could not read blueprint {}", entry.getKey(), e);
            }
        }
        out.sort(Comparator.comparingInt(Blueprint::order));
        return out;
    }

    private static Blueprint parse(ResourceLocation id, JsonObject json) {
        Map<Character, BlockState> key = new HashMap<>();
        JsonObject keyJson = json.getAsJsonObject("key");
        for (String symbol : keyJson.keySet()) {
            try {
                key.put(symbol.charAt(0), BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),
                        keyJson.get(symbol).getAsString(), false).blockState());
            } catch (Exception e) {
                TFMG.LOGGER.warn("Blueprint {} has an invalid block state {}", id, keyJson.get(symbol));
            }
        }
        JsonArray layers = json.getAsJsonArray("layers");
        int sizeX = 0, sizeZ = 0;
        for (JsonElement layer : layers) {
            sizeZ = Math.max(sizeZ, layer.getAsJsonArray().size());
            for (JsonElement row : layer.getAsJsonArray())
                sizeX = Math.max(sizeX, row.getAsString().length());
        }
        // Centre the structure on the anchor horizontally.
        int cx = sizeX / 2, cz = sizeZ / 2;
        List<Map<BlockPos, BlockState>> out = new ArrayList<>();
        for (int y = 0; y < layers.size(); y++) {
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    BlockState state = key.get(row.charAt(x));
                    if (state != null && !state.isAir())
                        blocks.put(new BlockPos(x - cx, y, z - cz), state);
                }
            }
            if (!blocks.isEmpty())
                out.add(blocks);
        }
        Map<String, String> name = new HashMap<>();
        JsonObject nameJson = json.getAsJsonObject("name");
        for (String lang : nameJson.keySet())
            name.put(lang, nameJson.get(lang).getAsString());
        String path = id.getPath();
        return new Blueprint(path.substring(path.lastIndexOf('/') + 1, path.length() - 5),
                json.has("line") ? json.get("line").getAsString() : "",
                json.has("order") ? json.get("order").getAsInt() : 1000, name, out, sizeX, sizeZ);
    }
}
