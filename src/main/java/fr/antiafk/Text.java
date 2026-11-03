package fr.antiafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convertit &c, &#rrggbb et MiniMessage (HTML-like) en Component. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private Text() {}

    public static String convert(String in) {
        String s = in.replace('§', '&');
        s = HEX.matcher(s).replaceAll("<reset><#$1>");
        Matcher m = CODE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char c = Character.toLowerCase(m.group(1).charAt(0));
            String rep = ("0123456789abcdef".indexOf(c) >= 0 ? "<reset>" : "") + "<" + TAGS.get(c) + ">";
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null) return Component.empty();
        String s = raw;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return MM.deserialize(convert(s)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
