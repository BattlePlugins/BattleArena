package org.battleplugins.arena.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ColorableArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@DocumentationSource("https://docs.battleplugins.org/books/user-guide/page/item-syntax")
public final class ItemStackParser implements ArenaConfigParser.Parser<ItemStack> {
    private static final Component NO_ITALIC = Component.empty().decoration(TextDecoration.ITALIC, false);

    @Override
    public ItemStack parse(Object object) throws ParseException {
        if (object instanceof String string) {
            return deserializeSingular(string);
        }

        if (object instanceof ConfigurationSection section) {
            return deserializeNode(section);
        }

        throw new ParseException("Invalid ItemStack for object: " + object)
                .cause(ParseException.Cause.INVALID_TYPE)
                .type(this.getClass())
                .userError();
    }

    public static ItemStack deserializeSingular(String contents) throws ParseException {
        ItemStack itemStack;

        SingularValueParser.ArgumentBuffer buffer;
        try {
            buffer = SingularValueParser.parseNamed(contents, SingularValueParser.BraceStyle.CURLY, ';');
        } catch (ParseException e) {
            // If we get an error, let's try parsing using Minecraft's native format
            try {
                return Bukkit.getItemFactory().createItemStack(contents);
            } catch (IllegalArgumentException ex) {
                ex.initCause(e);
                throw e;
            }
        }

        if (!buffer.hasNext()) {
            throw new ParseException("No data found for ItemStack")
                    .cause(ParseException.Cause.INVALID_TYPE)
                    .type(ItemStackParser.class)
                    .userError();
        }

        SingularValueParser.Argument root = buffer.pop();
        if (root.key().equals("root")) {
            Material material = Material.matchMaterial(root.value());
            if (material == null) {
                throw new ParseException("Invalid material " + root.value())
                        .cause(ParseException.Cause.INVALID_VALUE)
                        .type(ItemStackParser.class)
                        .userError();
            }

            itemStack = new ItemStack(material);
        } else {
            throw new ParseException("Invalid ItemStack root tag " + root.key())
                    .cause(ParseException.Cause.INTERNAL_ERROR)
                    .type(ItemStackParser.class);
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        while (buffer.hasNext()) {
            SingularValueParser.Argument argument = buffer.pop();
            String key = argument.key();
            String value = argument.value();
            switch (key) {
                case "color" -> {
                    if (itemMeta instanceof ColorableArmorMeta colorMeta) {
                        colorMeta.setColor(ColorParser.deserializeSingularBukkit(value));
                    } else if (itemMeta instanceof PotionMeta potionMeta) {
                        potionMeta.setColor(ColorParser.deserializeSingularBukkit(value));
                    }
                }
                case "custom-model-data" -> {
                    itemMeta.setCustomModelData(Integer.parseInt(value));
                }
                case "damage" -> {
                    if (itemMeta instanceof Damageable damageable) {
                        damageable.setDamage(Integer.parseInt(value));
                    }
                }
                case "display-name", "name" -> {
                    itemMeta.displayName(NO_ITALIC.append(MiniMessage.miniMessage().deserialize(value)));
                }
                case "enchants", "enchantments" -> {
                    for (String enchant : getList(value)) {
                        String[] del = enchant.split(":");
                        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.fromString(del[0]));
                        if (enchantment == null) {
                            throw new ParseException("Invalid enchantment " + del[0]);
                        }

                        itemMeta.addEnchant(enchantment, Integer.parseInt(del[1]), true);
                    }
                }
                case "item-flags" -> {
                    Set<ItemFlag> flags = new HashSet<>();
                    for (String itemFlag : getList(value)) {
                        ItemFlag flag = ItemFlag.valueOf(itemFlag.toUpperCase(Locale.ROOT));
                        flags.add(flag);
                    }

                    itemMeta.addItemFlags(flags.toArray(new ItemFlag[0]));
                }
                case "lore" -> {
                    List<Component> lore = getList(value)
                            .stream()
                            .map(MiniMessage.miniMessage()::deserialize)
                            .map(NO_ITALIC::append)
                            .toList();

                    itemMeta.lore(lore);
                }
                case "quantity", "amount", "count" -> {
                    itemStack.setAmount(Integer.parseInt(value));
                }
                case "unbreakable" -> {
                    itemMeta.setUnbreakable(Boolean.parseBoolean(value));
                }
                case "effects", "potion-effects" -> {
                    if (!(itemMeta instanceof PotionMeta potionMeta)) {
                        continue;
                    }

                    for (String effect : getList(value)) {
                        PotionEffect potionEffect = PotionEffectParser.deserializeSingular(effect);
                        potionMeta.addCustomEffect(potionEffect, true);
                    }
                }
            }
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static ItemStack deserializeNode(ConfigurationSection section) throws ParseException {
        String materialName = section.getString("item", section.getString("type", section.getString("material")));
        if (materialName == null) {
            throw new ParseException("No material found for ItemStack")
                    .cause(ParseException.Cause.INVALID_VALUE)
                    .type(ItemStackParser.class)
                    .userError();
        }

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            throw new ParseException("Invalid material " + materialName)
                    .cause(ParseException.Cause.INVALID_VALUE)
                    .type(ItemStackParser.class)
                    .userError();
        }

        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        for (String meta : section.getKeys(false)) {
            switch (meta) {
                case "color": {
                    if (itemMeta instanceof ColorableArmorMeta colorMeta) {
                        colorMeta.setColor(ColorParser.deserializeSingularBukkit(section.getString(meta)));
                    } else if (itemMeta instanceof PotionMeta potionMeta) {
                        potionMeta.setColor(ColorParser.deserializeSingularBukkit(section.getString(meta)));
                    }
                }
                case "custom-model-data": {
                    itemMeta.setCustomModelData(section.getInt(meta));
                }
                case "damage": {
                    if (itemMeta instanceof Damageable damageable) {
                        damageable.setDamage(section.getInt(meta));
                    }
                }
                case "display-name", "name": {
                    itemMeta.displayName(NO_ITALIC.append(MiniMessage.miniMessage().deserialize(section.getString(meta))));
                }
                case "enchants", "enchantments": {
                    for (String enchant : section.getStringList(meta)) {
                        String[] del = enchant.split(":");
                        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.fromString(del[0]));
                        if (enchantment == null) {
                            throw new ParseException("Invalid enchantment " + del[0]);
                        }

                        itemMeta.addEnchant(enchantment, Integer.parseInt(del[1]), true);
                    }
                }
                case "item-flags": {
                    Set<ItemFlag> flags = new HashSet<>();
                    for (String itemFlag : section.getStringList(meta)) {
                        ItemFlag flag = ItemFlag.valueOf(itemFlag.toUpperCase(Locale.ROOT));
                        flags.add(flag);
                    }

                    itemMeta.addItemFlags(flags.toArray(new ItemFlag[0]));
                }
                case "lore": {
                    List<Component> lore = section.getStringList(meta)
                            .stream()
                            .map(MiniMessage.miniMessage()::deserialize)
                            .map(NO_ITALIC::append)
                            .toList();

                    itemMeta.lore(lore);
                }
                case "quantity", "amount", "count": {
                    itemStack.setAmount(section.getInt(meta));
                }
                case "unbreakable": {
                    itemMeta.setUnbreakable(section.getBoolean(meta));
                }
                case "effects", "potion-effects": {
                    if (!(itemMeta instanceof PotionMeta potionMeta)) {
                        continue;
                    }

                    for (String effect : section.getStringList(meta)) {
                        PotionEffect potionEffect = PotionEffectParser.deserializeSingular(effect);
                        potionMeta.addCustomEffect(potionEffect, true);
                    }
                }
            }
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static List<String> getList(String value) {
        return Arrays.asList(value.replace("[", "")
                .replace("]", "").split(","));
    }
}
