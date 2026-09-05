package com.manager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the stored chat history so retrieval can understand a turn that only
 * makes sense after the previous one.
 *
 * <p>A citizen who is asked a clarifying question answers it the way people
 * answer people — "ha, malakam bor". On its own that sentence carries no legal
 * terms, so searching Lex.uz with it finds nothing and the assistant would
 * report "no normative document found" one turn after it cited one. Resolving
 * the follow-up against the question it continues keeps retrieval on the topic
 * the conversation is actually about.</p>
 */
public final class ConversationContext {

    private ConversationContext() {}

    /** Turns fed to the planner as context; older ones no longer steer the topic. */
    private static final int MAX_CONTEXT_MESSAGES = 4;
    private static final int MAX_MESSAGE_CHARS = 600;

    /** A message this short cannot carry its own topic. */
    private static final int SELF_CONTAINED_MIN_TOKENS = 7;

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'ʻʼ’‘-]*");

    /** Openings that continue the previous turn rather than start a new topic. */
    private static final Set<String> CONTINUATION_OPENERS = Set.of(
            "ha", "yo'q", "yoq", "yes", "no", "да", "нет",
            "bor", "yuq", "mavjud", "to'g'ri", "togri", "shunday", "shu",
            "unda", "keyin", "davom", "xo'p", "xop", "rahmat", "ok", "okay",
            "batafsil", "tushuntiring", "tushuntir", "aytib", "yana");

    /**
     * The text retrieval should search with. A self-contained question is used
     * as written; a follow-up is prefixed with the question it answers so the
     * keyword search still sees the legal subject of the conversation.
     */
    public static String retrievalQuestion(String message, List<Map<String, Object>> history) {
        String current = normalize(message);
        if (current.isEmpty()) return current;
        if (!isFollowUp(current)) return current;

        String previous = lastUserQuestion(history);
        if (previous.isEmpty()) return current;
        return previous + "\n" + current;
    }

    /**
     * True when the message leans on earlier turns for its meaning — a short
     * reply, or one that opens with an affirmation or a continuation word.
     */
    public static boolean isFollowUp(String message) {
        String normalized = normalize(message);
        if (normalized.isEmpty()) return false;

        List<String> tokens = tokenize(normalized);
        if (tokens.isEmpty()) return true;
        if (CONTINUATION_OPENERS.contains(tokens.get(0))) return true;
        return tokens.size() < SELF_CONTAINED_MIN_TOKENS;
    }

    /** The most recent question the citizen asked, or "" when there is none. */
    public static String lastUserQuestion(List<Map<String, Object>> history) {
        if (history == null) return "";
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> message = history.get(i);
            if (!"user".equals(roleOf(message))) continue;
            String text = normalize(textOf(message));
            if (!text.isEmpty()) return truncate(text);
        }
        return "";
    }

    /** True when the assistant has already produced at least one answer here. */
    public static boolean hasAssistantTurn(List<Map<String, Object>> history) {
        if (history == null) return false;
        return history.stream()
                .anyMatch(message -> "model".equals(roleOf(message)) && !textOf(message).isBlank());
    }

    /**
     * The recent turns rendered as plain dialogue for a prompt. Assistant turns
     * are truncated: the planner needs the topic, not the whole legal answer.
     */
    public static String transcript(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) return "";

        List<Map<String, Object>> recent = history.size() > MAX_CONTEXT_MESSAGES
                ? history.subList(history.size() - MAX_CONTEXT_MESSAGES, history.size())
                : history;

        List<String> lines = new ArrayList<>();
        for (Map<String, Object> message : recent) {
            String text = normalize(textOf(message));
            if (text.isEmpty()) continue;
            String speaker = "model".equals(roleOf(message)) ? "Yordamchi" : "Fuqaro";
            lines.add(speaker + ": " + truncate(text));
        }
        return String.join("\n", lines);
    }

    /** Extracts the text of a stored {@code {role, parts:[{text}]}} message. */
    public static String textOf(Map<String, Object> message) {
        if (message == null) return "";
        Object parts = message.get("parts");
        if (!(parts instanceof List<?> items)) return "";

        StringBuilder text = new StringBuilder();
        for (Object item : items) {
            if (item instanceof Map<?, ?> part) {
                Object value = part.get("text");
                if (value != null) text.append(value);
            }
        }
        return text.toString();
    }

    private static String roleOf(Map<String, Object> message) {
        if (message == null) return "";
        Object role = message.get("role");
        return role == null ? "" : role.toString();
    }

    private static List<String> tokenize(String text) {
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_MESSAGE_CHARS
                ? text
                : text.substring(0, MAX_MESSAGE_CHARS).trim() + "...";
    }
}
