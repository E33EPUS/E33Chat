package com.niuqu.chatbubble;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
//#if MC < 11900
//$$ import net.minecraft.text.LiteralText;
//$$ import net.minecraft.text.TranslatableText;
//#endif

/**
 * Text factory compatibility for pre-1.19 and modern Text APIs.
 */
public final class Txt {
    private Txt() {}

    public static MutableText literal(String text) {
        //#if MC >= 11900
        return Text.literal(text);
        //#else
        //$$ return new LiteralText(text);
        //#endif
    }

    public static MutableText translatable(String key, Object... args) {
        //#if MC >= 11900
        return Text.translatable(key, args);
        //#else
        //$$ return new TranslatableText(key, args);
        //#endif
    }

    public static MutableText empty() {
        //#if MC >= 11900
        return Text.empty();
        //#else
        //$$ return new LiteralText("");
        //#endif
    }

    public static Style applyFormattingCode(Style style, char code) {
        switch (Character.toLowerCase(code)) {
            case '0': return style.withColor(Formatting.BLACK);
            case '1': return style.withColor(Formatting.DARK_BLUE);
            case '2': return style.withColor(Formatting.DARK_GREEN);
            case '3': return style.withColor(Formatting.DARK_AQUA);
            case '4': return style.withColor(Formatting.DARK_RED);
            case '5': return style.withColor(Formatting.DARK_PURPLE);
            case '6': return style.withColor(Formatting.GOLD);
            case '7': return style.withColor(Formatting.GRAY);
            case '8': return style.withColor(Formatting.DARK_GRAY);
            case '9': return style.withColor(Formatting.BLUE);
            case 'a': return style.withColor(Formatting.GREEN);
            case 'b': return style.withColor(Formatting.AQUA);
            case 'c': return style.withColor(Formatting.RED);
            case 'd': return style.withColor(Formatting.LIGHT_PURPLE);
            case 'e': return style.withColor(Formatting.YELLOW);
            case 'f': return style.withColor(Formatting.WHITE);
            case 'k': return style.withFormatting(Formatting.OBFUSCATED);
            case 'l': return style.withBold(true);
            case 'm': return style.withFormatting(Formatting.STRIKETHROUGH);
            case 'n': return style.withUnderline(true);
            case 'o': return style.withItalic(true);
            case 'r': return Style.EMPTY;
            default: return style;
        }
    }
}
