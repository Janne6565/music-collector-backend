package com.musiccollector.services.mail;

import com.musiccollector.configuration.MailProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The one shell every transactional mail is set in.
 *
 * <p>Drawn as board 1a of the "Transactional E-mails" deck and built to the constraints that
 * board was drawn against, none of which are style choices:
 *
 * <ul>
 *   <li><b>Tables and inline styles.</b> A 600px single-column table, no flex, no grid, no
 *       positioning — the layout engines this has to survive are twenty years old.
 *   <li><b>No image carries meaning.</b> Gmail blocks remote images by default, so the
 *       wordmark is set type and there is not a single {@code <img>} in the output.
 *   <li><b>No webfont dependency.</b> Manrope and Newsreader are named first and
 *       system-ui / Georgia carry the design when they are stripped, which is most of the time.
 *   <li><b>Every destination appears twice</b> — once as a bulletproof button, once as a
 *       full URL in plain type, for the clients that rewrite or strip anchors.
 *   <li><b>Flattened hex, never alpha.</b> The app's tokens are rgba over paper; Outlook
 *       renders alpha unreliably, so the deck's flattened values are used instead.
 * </ul>
 *
 * <p>The narrow and dark variants (boards 1g, 1h) are the same markup under two media
 * queries in the head — the only place a stylesheet is allowed, and the reason every themed
 * element carries a class beside its inline style.
 */
@Component
public class MailTemplate {

    // Light, flattened from the app's alpha tokens over #faf8f5.
    private static final String CANVAS = "#efece6";
    private static final String PAPER = "#faf8f5";
    private static final String BORDER = "#e6e4e1";
    private static final String INK = "#191713";
    private static final String TEXT = "#4a4741";
    private static final String MUTED = "#7e7c79";
    private static final String FAINT = "#9c9996";
    private static final String NOTE = "#f1ece3";
    private static final String FOOTER = "#f1ede6";
    private static final String SEPARATOR = "#c8c3ba";
    private static final String ACCENT = "#a2573a";
    private static final String ACCENT_STRONG = "#8c4530";

    private static final String SANS = "'Manrope',system-ui,sans-serif";
    private static final String SERIF = "'Newsreader',Georgia,serif";
    private static final String MONO = "ui-monospace,Menlo,monospace";

    /** Board 1i wraps the plain-text twin here, which is narrow enough for any client. */
    private static final int TEXT_WIDTH = 62;

    /**
     * Who runs Music Collector, in the form § 5 DDG asks for it.
     *
     * <p>The authority is {@code legal/operator.ts} in the shared package, which every screen
     * reads from. Mail cannot import TypeScript, so this is the one duplicate — change it in
     * both or the Impressum and the mail footer will disagree, which is worse than neither.
     */
    private static final String OPERATOR_LINE = "Janne Keipert · Marchlewskistraße 102 · 10243 Berlin";

    private static final String[][] LEGAL_LINKS = {
        {"Impressum", "/legal/impressum"},
        {"Datenschutz", "/legal/datenschutz"},
        {"Nutzungsbedingungen", "/legal/nutzungsbedingungen"},
    };

    private final MailProperties properties;

    public MailTemplate(MailProperties properties) {
        this.properties = properties;
    }

    /** The app's public base URL with no trailing slash, for building links into it. */
    public String publicUrl() {
        String value = properties.publicUrl() == null ? "" : properties.publicUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public RenderedMail render(MailContent content) {
        return new RenderedMail(content.subject(), html(content), text(content));
    }

    // ---------------------------------------------------------------- HTML

    private String html(MailContent content) {
        StringBuilder rows = new StringBuilder();

        row(rows, "34px 40px 0", "<div class=\"mc-ink\" style=\"font:400 21px/1 %s;color:%s\">Music Collector</div>"
                .formatted(SERIF, INK));
        row(rows, "22px 40px 0", rule());
        row(
                rows,
                "34px 40px 0",
                "<div class=\"mc-ink mc-h1\" style=\"font:400 27px/1.25 %s;color:%s;letter-spacing:-.005em\">%s</div>"
                        .formatted(SERIF, INK, esc(content.headline())));

        boolean first = true;
        for (String paragraph : content.paragraphs()) {
            row(
                    rows,
                    (first ? "16px" : "14px") + " 40px 0",
                    "<div class=\"mc-text\" style=\"font:400 15px/1.68 %s;color:%s\">%s</div>"
                            .formatted(SANS, TEXT, esc(paragraph)));
            first = false;
        }

        if (!content.facts().isEmpty()) {
            row(
                    rows,
                    "22px 40px 0",
                    "<div class=\"mc-mono\" style=\"font:400 12px/1.8 %s;color:%s\">%s</div>"
                            .formatted(MONO, MUTED, String.join("<br>", content.facts().stream().map(MailTemplate::esc).toList())));
        }

        if (!content.rows().isEmpty()) {
            // Plain rows rather than a table of their own: a nested grid is the first thing
            // a mail client breaks, and a record is two lines of type, not a layout.
            boolean firstRow = true;
            for (MailContent.Row entry : content.rows()) {
                row(
                        rows,
                        (firstRow ? "22px" : "16px") + " 40px 0",
                        ("<div class=\"mc-ink\" style=\"font:600 14px/1.4 %s;color:%s\">%s</div>"
                                        + "<div class=\"mc-mono\" style=\"font:400 12.5px/1.5 %s;color:%s;"
                                        + "padding-top:2px\">%s</div>")
                                .formatted(SANS, INK, esc(entry.title()), SANS, MUTED, esc(entry.detail())));
                firstRow = false;
            }
        }

        if (content.action() != null) {
            row(rows, "26px 40px 0", button(content.action()));
            row(
                    rows,
                    "16px 40px 0",
                    ("<div class=\"mc-mono\" style=\"font:400 12px/1.7 %s;color:%s;word-break:break-all;"
                                    + "overflow-wrap:break-word\">Or paste this into your browser:<br>%s</div>")
                            .formatted(MONO, MUTED, esc(content.action().url())));
        }

        if (content.note() != null) {
            row(rows, "26px 40px 0", note(content.note()));
        }

        if (content.closing() != null) {
            row(
                    rows,
                    "26px 40px 0",
                    "<div class=\"mc-text\" style=\"font:400 15px/1.68 %s;color:%s;font-style:italic\">%s</div>"
                            .formatted(SERIF, TEXT, esc(content.closing())));
        }

        row(rows, "34px 0 0", footer(content.footerReason(), content.unsubscribe()));

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light dark">
                <meta name="supported-color-schemes" content="light dark">
                <title>%s</title>
                %s
                </head>
                <body class="mc-body" style="margin:0;padding:0;background:%s">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" \
                class="mc-body" style="width:100%%;border-collapse:collapse;background:%s">
                <tbody><tr><td class="mc-gutter" align="center" style="padding:24px">
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" class="mc-card" \
                style="width:600px;max-width:600px;border-collapse:collapse;background:%s;border:1px solid %s">
                <tbody>%s</tbody>
                </table>
                </td></tr></tbody></table>
                </body>
                </html>
                """
                .formatted(esc(content.subject()), STYLE, CANVAS, esc(preheader(content)), CANVAS, PAPER, BORDER, rows);
    }

    private static String preheader(MailContent content) {
        return content.paragraphs().isEmpty() ? content.headline() : content.paragraphs().getFirst();
    }

    private static void row(StringBuilder rows, String padding, String cell) {
        rows.append("<tr><td class=\"mc-pad\" style=\"padding:%s\">%s</td></tr>".formatted(padding, cell));
    }

    private static String rule() {
        return "<div class=\"mc-rule\" style=\"height:1px;background:%s;font-size:1px;line-height:1px\">&#8203;</div>"
                .formatted(BORDER);
    }

    /**
     * A solid table cell rather than a styled anchor: Outlook ignores padding and background
     * on an inline element, and an image would be blocked. At narrow widths the wrapper goes
     * full width so the tap target survives (board 1g).
     */
    private static String button(MailContent.Action action) {
        return ("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"mc-btn\" "
                        + "style=\"border-collapse:collapse\"><tbody><tr>"
                        + "<td class=\"mc-btn-cell\" style=\"background:%s;padding:15px 30px;border-radius:2px\">"
                        + "<a href=\"%s\" style=\"font:700 15px/1 %s;color:#ffffff;text-decoration:none;"
                        + "display:inline-block\">%s</a></td></tr></tbody></table>")
                .formatted(ACCENT, esc(action.url()), SANS, esc(action.label()));
    }

    private static String note(MailContent.Note note) {
        StringBuilder inner = new StringBuilder();
        if (note.heading() != null) {
            inner.append("<div class=\"mc-ink\" style=\"font:600 13.5px/1.62 %s;color:%s\">%s</div>"
                    .formatted(SANS, INK, esc(note.heading())));
        }
        inner.append("<div class=\"mc-text\" style=\"font:400 13.5px/1.62 %s;color:%s%s\">%s</div>"
                .formatted(SANS, TEXT, note.heading() == null ? "" : ";padding-top:5px", esc(note.text())));
        if (note.linkLabel() != null) {
            inner.append("<div style=\"padding-top:11px\"><a class=\"mc-link\" href=\"%s\" style=\"font:600 13.5px/1.5 %s;color:%s\">%s</a></div>"
                    .formatted(esc(note.linkUrl()), SANS, ACCENT_STRONG, esc(note.linkLabel())));
            if (note.showLinkUrl()) {
                inner.append(("<div class=\"mc-mono\" style=\"font:400 11.5px/1.7 %s;color:%s;padding-top:8px;"
                                + "word-break:break-all;overflow-wrap:break-word\">%s</div>")
                        .formatted(MONO, FAINT, esc(note.linkUrl())));
            }
        }
        return ("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"mc-note\" "
                        + "style=\"width:100%%;border-collapse:collapse;background:%s\"><tbody><tr>"
                        + "<td class=\"mc-note-pad\" style=\"padding:17px 19px\">%s</td></tr></tbody></table>")
                .formatted(NOTE, inner);
    }

    private String footer(String reason, MailContent.Unsubscribe unsubscribe) {
        StringBuilder links = new StringBuilder();
        for (String[] link : LEGAL_LINKS) {
            if (!links.isEmpty()) {
                links.append(" <span class=\"mc-sep\" style=\"color:%s\">·</span> ".formatted(SEPARATOR));
            }
            links.append("<a class=\"mc-link\" href=\"%s\" style=\"color:%s\">%s</a>"
                    .formatted(esc(publicUrl() + link[1]), ACCENT_STRONG, link[0]));
        }
        // The slot the shell has always had and nothing used until the digest. It names the
        // one category being switched off, because a link that also silenced security
        // notices would be a trap.
        String stop = unsubscribe == null
                ? ""
                : ("<div class=\"mc-mono-body\" style=\"font:400 12.5px/1.62 %s;color:%s;padding-top:8px\">"
                                + "<a class=\"mc-link\" href=\"%s\" style=\"color:%s\">%s</a> — %s</div>")
                        .formatted(SANS, MUTED, esc(unsubscribe.url()), ACCENT_STRONG, esc(unsubscribe.label()),
                                esc(unsubscribe.what()));

        return ("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"mc-footer\" "
                        + "style=\"width:100%%;border-collapse:collapse;background:%s;border-top:1px solid %s\">"
                        + "<tbody><tr><td class=\"mc-pad\" style=\"padding:22px 40px 24px\">"
                        + "<div class=\"mc-mono-body\" style=\"font:400 12.5px/1.62 %s;color:%s\">%s</div>"
                        + "%s"
                        + "<div class=\"mc-faint\" style=\"font:400 12px/1.62 %s;color:%s;padding-top:12px\">%s</div>"
                        + "<div style=\"font:400 12px/1.62 %s;padding-top:6px\">%s</div>"
                        + "</td></tr></tbody></table>")
                .formatted(FOOTER, BORDER, SANS, MUTED, esc(reason), stop, SANS, FAINT, OPERATOR_LINE, SANS, links);
    }

    /**
     * The only stylesheet in the message, and the only place the narrow and dark variants
     * exist. Every rule is {@code !important} because it has to beat an inline style, and
     * every selector is a class because attribute and element selectors are the first thing
     * a mail client's sanitiser drops.
     */
    private static final String STYLE =
            """
            <style>
            @media only screen and (max-width:620px){
              .mc-gutter{padding:12px !important}
              .mc-card{width:100% !important}
              .mc-pad{padding-left:22px !important;padding-right:22px !important}
              .mc-note-pad{padding:15px 16px !important}
              .mc-h1{font-size:23px !important;line-height:1.22 !important}
              .mc-btn{width:100% !important}
              .mc-btn-cell{text-align:center !important;padding:16px 18px !important}
            }
            @media (prefers-color-scheme:dark){
              .mc-body{background:#100e0c !important}
              .mc-card{background:#1b1815 !important;border-color:#302b25 !important}
              .mc-ink{color:#f2ece1 !important}
              .mc-text{color:#cfc7ba !important}
              .mc-mono,.mc-mono-body{color:#9a9287 !important}
              .mc-faint{color:#7d766c !important}
              .mc-rule{background:#302b25 !important}
              .mc-note{background:#241f1a !important}
              .mc-footer{background:#171410 !important;border-top-color:#302b25 !important}
              .mc-link{color:#d0836a !important}
              .mc-sep{color:#4a443c !important}
            }
            </style>""";

    // ---------------------------------------------------------------- Plain text

    /**
     * The twin that ships in the same message (board 1i). Not a fallback nobody reads: it is
     * what a screen reader, a terminal client and every spam filter sees, and a mail whose
     * copy only works with a button behind it is a mail that fails all three.
     */
    private String text(MailContent content) {
        List<String> out = new ArrayList<>();
        out.add("MUSIC COLLECTOR");
        out.add("-".repeat(TEXT_WIDTH));
        out.add("");
        out.add(plain(content.headline()));
        out.add("");
        for (String paragraph : content.paragraphs()) {
            out.addAll(wrap(plain(paragraph)));
            out.add("");
        }
        if (!content.facts().isEmpty()) {
            content.facts().forEach(fact -> out.add(plain(fact)));
            out.add("");
        }
        for (MailContent.Row entry : content.rows()) {
            out.addAll(wrap(plain(entry.title())));
            out.addAll(wrap("  " + plain(entry.detail())));
            out.add("");
        }
        if (content.action() != null) {
            out.add(plain(content.action().label()) + ":");
            // Alone on its line, so no client folds a URL in half.
            out.add(content.action().url());
            out.add("");
        }
        if (content.note() != null) {
            if (content.note().heading() != null) {
                out.add(plain(content.note().heading()));
            }
            out.addAll(wrap(plain(content.note().text())));
            out.add("");
            if (content.note().linkLabel() != null) {
                out.add(plain(content.note().linkLabel()) + ":");
                out.add(content.note().linkUrl());
                out.add("");
            }
        }
        if (content.closing() != null) {
            out.addAll(wrap(plain(content.closing())));
            out.add("");
        }
        out.add("-".repeat(TEXT_WIDTH));
        out.addAll(wrap(plain(content.footerReason())));
        out.add("");
        if (content.unsubscribe() != null) {
            out.addAll(wrap(plain(content.unsubscribe().label()) + " - " + plain(content.unsubscribe().what())));
            out.add(content.unsubscribe().url());
            out.add("");
        }
        // Spelled out rather than linked: there are no anchors here to hide it in, and the
        // provider still has to be identifiable.
        out.add("Janne Keipert");
        out.add("Marchlewskistrasse 102, 10243 Berlin");
        out.add("");
        for (String[] link : LEGAL_LINKS) {
            out.add("%-21s %s".formatted(link[0] + ":", publicUrl() + link[1]));
        }
        return String.join("\n", out) + "\n";
    }

    /** Typographic marks the HTML wants and a monospace column does not. */
    private static String plain(String value) {
        return value.replace('’', '\'')
                .replace('‘', '\'')
                .replace("—", "-")
                .replace("–", "-")
                .replace('\u00a0', ' ');
    }

    private static List<String> wrap(String paragraph) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > TEXT_WIDTH) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ---------------------------------------------------------------- Escaping

    /**
     * Applied to every interpolated value without exception.
     *
     * <p>Most of what goes in is our own copy, but an e-mail address and a display name are
     * whatever somebody typed, and a mail is HTML sent to a third party's renderer.
     */
    private static String esc(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
