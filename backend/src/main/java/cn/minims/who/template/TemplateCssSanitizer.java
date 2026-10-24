package cn.minims.who.template;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 模板包里唯一允许进入页面的外部代码就是这段 CSS，所以它按「重新生成」而不是「过滤回声」来处理：
 * 解析出规则后由这里重新拼字符串，作者原文里的任何夹带都进不了产物。
 *
 * 红线：不解析也禁止任何 JS 语义（expression、-moz-binding、behavior）与跳出标签的可能（字符 &lt;）。
 * 作用域：每条选择器必须以模板自己的作用域类开头，防止一份社区模板改坏整站或别人的简历。
 */
public final class TemplateCssSanitizer {

    public static final int MAX_CSS_CHARS = 16 * 1024;

    /** 属性名：CSS 自定义属性或普通属性，允许厂商前缀，不接受 * 之类的 hack。 */
    private static final Pattern PROPERTY = Pattern.compile("^(--[A-Za-z0-9_-]{1,64}|[a-z][a-z0-9-]{1,40})$");
    /** data: 之外的 url() 一律不收：相对路径没有可靠基准，外链既是隐私面也是内容泄露面。 */
    private static final Pattern URL_TOKEN = Pattern.compile("url\\s*\\(\\s*([\"']?)(.*?)\\1\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCOPE_START = Pattern.compile("^[a-z0-9-]+$");

    private TemplateCssSanitizer() {
    }

    /**
     * @param scopeClass 形如 tw-jinshi 的作用域类，必须已在别处按安全字符集校验过
     * @return 重新生成的 CSS；style 为空时返回 null
     * @throws TemplatePackageException 携带全部不合规原因
     */
    public static String sanitize(String css, String scopeClass) {
        List<String> problems = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return null;
        }
        if (!SCOPE_START.matcher(scopeClass.replaceFirst("^tw-", "")).matches()) {
            problems.add("作用域类不合法，无法安全注入");
        }
        if (css.length() > MAX_CSS_CHARS) {
            problems.add("style 超过 " + MAX_CSS_CHARS + " 字符上限");
        }
        String stripped = stripComments(css, problems);
        if (!problems.isEmpty()) {
            throw new TemplatePackageException(problems);
        }
        String cleaned = rebuild(stripped, scopeClass, problems, 0);
        if (!problems.isEmpty()) {
            throw new TemplatePackageException(problems);
        }
        if (cleaned.isBlank()) {
            problems.add("style 里没有任何可保留的规则");
            throw new TemplatePackageException(problems);
        }
        return cleaned;
    }

    private static String stripComments(String css, List<String> problems) {
        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        while (i < css.length()) {
            int open = css.indexOf("/*", i);
            if (open < 0) {
                out.append(css, i, css.length());
                break;
            }
            int close = css.indexOf("*/", open + 2);
            if (close < 0) {
                problems.add("style 里有未闭合的注释");
                break;
            }
            out.append(css, i, open).append(' ');
            i = close + 2;
        }
        return out.toString();
    }

    /**
     * 逐块解析并重新拼装。at-rule 只允许 @media / @supports 再嵌套一层规则，其余一律拒绝。
     */
    private static String rebuild(String css, String scopeClass, List<String> problems, int depth) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int length = css.length();
        while (i < length) {
            int brace = indexOutsideQuote(css, '{', i);
            if (brace < 0) {
                String tail = css.substring(i).trim();
                if (!tail.isEmpty()) {
                    problems.add("style 里有未闭合的规则块：" + preview(tail));
                }
                break;
            }
            String prelude = css.substring(i, brace).trim();
            int end = matchingBrace(css, brace);
            if (end < 0) {
                problems.add("style 里有未闭合的规则块：" + preview(prelude));
                break;
            }
            String body = css.substring(brace + 1, end);
            if (prelude.isEmpty()) {
                problems.add("style 里有缺少选择器的规则块");
            } else if (prelude.startsWith("@")) {
                String atRule = prelude.split("[\\s(]", 2)[0].toLowerCase();
                if (depth > 0 || !(atRule.equals("@media") || atRule.equals("@supports"))) {
                    problems.add("style 里不支持的 at-rule：" + preview(prelude));
                } else {
                    String nested = rebuild(body, scopeClass, problems, depth + 1);
                    out.append(prelude).append(" {\n").append(nested).append("}\n");
                }
            } else {
                out.append(rules(prelude, body, scopeClass, problems));
            }
            i = end + 1;
        }
        return out.toString();
    }

    private static String rules(String prelude, String body, String scopeClass, List<String> problems) {
        StringBuilder declarations = new StringBuilder();
        for (String selector : splitTopLevel(prelude, ',')) {
            String trimmed = selector.trim();
            if (!startsWithScope(trimmed, scopeClass)) {
                problems.add("选择器必须以 ." + scopeClass + " 开头，避免影响别人的简历与站点：" + preview(trimmed));
                continue;
            }
            checkValue(trimmed, problems, "选择器");
            declarations.append(trimmed).append(" {\n");
            for (String declaration : splitTopLevel(body, ';')) {
                String item = declaration.trim();
                if (item.isEmpty()) {
                    continue;
                }
                int colon = item.indexOf(':');
                if (colon <= 0) {
                    problems.add("声明写法不对：" + preview(item));
                    continue;
                }
                String property = item.substring(0, colon).trim().toLowerCase();
                String value = item.substring(colon + 1).trim();
                if (!PROPERTY.matcher(property).matches()) {
                    problems.add("属性名不被允许：" + preview(property));
                    continue;
                }
                if (property.equals("behavior") || property.equals("-ms-binding") || property.equals("-moz-binding")) {
                    problems.add("属性 " + property + " 有历史脚本语义，模板包不允许使用");
                    continue;
                }
                if (value.isEmpty()) {
                    problems.add("属性 " + property + " 的值为空");
                    continue;
                }
                checkValue(value, problems, "属性 " + property);
                checkUrls(value, problems);
                declarations.append("  ").append(property).append(": ").append(value).append(";\n");
            }
            declarations.append("}\n");
        }
        return declarations.toString();
    }

    private static boolean startsWithScope(String selector, String scopeClass) {
        String prefix = "." + scopeClass;
        if (!selector.startsWith(prefix)) {
            return false;
        }
        // .tw-jinshi-x 是另一个类，不能算命中作用域。
        return selector.length() == prefix.length() || !isIdentChar(selector.charAt(prefix.length()));
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static void checkValue(String text, List<String> problems, String where) {
        // 只禁 < ：它是唯一能提前闭合注入标签的字符；子选择器用的 > 必须保留。
        if (text.indexOf('<') >= 0) {
            problems.add(where + " 里不能出现 < ，否则会破坏注入的 style 标签：" + preview(text));
            return;
        }
        String lower = text.toLowerCase();
        for (String banned : List.of("!important", "expression(", "javascript:", "behavior:", "-moz-binding", "@import")) {
            if (lower.contains(banned)) {
                problems.add(where + " 里不允许出现「" + banned + "」：" + preview(text));
                return;
            }
        }
    }

    private static void checkUrls(String value, List<String> problems) {
        var matcher = URL_TOKEN.matcher(value);
        while (matcher.find()) {
            String target = matcher.group(2).trim();
            if (!target.toLowerCase().startsWith("data:")) {
                problems.add("url() 只接受 data: 内联资源，外链会让导出结果依赖别人的服务器：" + preview(target));
            }
        }
    }

    private static int indexOutsideQuote(String css, char needle, int from) {
        char quote = 0;
        for (int i = from; i < css.length(); i += 1) {
            char c = css.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i += 1;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == needle) {
                return i;
            }
        }
        return -1;
    }

    private static int matchingBrace(String css, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < css.length(); i += 1) {
            char c = css.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i += 1;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{') {
                depth += 1;
            } else if (c == '}') {
                depth -= 1;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 只在括号与引号之外切分，:is(a,b) 里的逗号不会把选择器切碎。 */
    private static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int paren = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i += 1) {
            char c = text.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == '\\') {
                    if (i + 1 < text.length()) {
                        current.append(text.charAt(i + 1));
                    }
                    i += 1;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '(') {
                paren += 1;
            } else if (c == ')') {
                paren = Math.max(0, paren - 1);
            }
            if (c == separator && paren == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    private static String preview(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 48 ? flat : flat.substring(0, 48) + "…";
    }
}
