package com.example.helpfulworkers;

import net.minecraft.network.chat.Component;

public record ActionResult(boolean success, Component message) {
    public static ActionResult ok(String text) {
        return new ActionResult(true, Component.literal(text));
    }

    public static ActionResult ok(Component text) {
        return new ActionResult(true, text);
    }

    public static ActionResult fail(String text) {
        return new ActionResult(false, Component.literal(text));
    }

    public static ActionResult fail(Component text) {
        return new ActionResult(false, text);
    }

    public String text() {
        return message.getString();
    }
}
