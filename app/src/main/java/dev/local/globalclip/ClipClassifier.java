package dev.local.globalclip;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯规则的内容识别器：不联网、不依赖模型，几十微秒内把一段剪贴内容
 * 分成验证码 / 链接 / 联系方式 / 账号密钥 / 代码 / 命令 / 路径 / 金额 /
 * 地址 / 单号数字 / 长文 / 短句，并抽出最适合一键复制的那个值。
 */
public final class ClipClassifier {

    public static final class Result {
        public final String category;
        public final String title;
        public final String extract;
        public final String detail;

        Result(String category, String title, String extract, String detail) {
            this.category = category;
            this.title = title;
            this.extract = extract;
            this.detail = detail;
        }
    }

    private static final Pattern URL_RE = Pattern.compile("(?i)(?:https?|ftp)://[^\\s<>\"'`]+");
    private static final Pattern WWW_RE = Pattern.compile("(?i)www\\.[^\\s<>\"'`]+");
    private static final Pattern BARE_LINK_RE = Pattern.compile(
            "(?i)^([a-z0-9][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)+)(/[^\\s]*)?$");

    private static final Pattern EMAIL_RE = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern MOBILE_RE = Pattern.compile(
            "(?<!\\d)(?:\\+?86[\\s-]?)?1[3-9]\\d(?:[\\s-]?\\d){8}(?!\\d)");
    private static final Pattern SERVICE_RE = Pattern.compile(
            "(?<!\\d)(?:400|800)[\\s-]?\\d{3}[\\s-]?\\d{4}(?!\\d)");
    private static final Pattern LANDLINE_RE = Pattern.compile(
            "(?<!\\d)0\\d{2,3}[\\s-]?\\d{6,8}(?!\\d)");

    private static final Pattern OTP_CONTEXT_RE = Pattern.compile(
            "(验证码|校验码|动态码|短信码|确认码|激活码|邀请码|安全码|验证口令|code|otp|passcode|verification code)"
                    + "[^0-9A-Za-z]{0,14}([0-9A-Za-z]{4,8})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIX_DIGITS_RE = Pattern.compile("^\\s*(\\d{6})\\s*$");
    private static final Pattern STRONG_OTP_RE = Pattern.compile(
            "(验证码|校验码|动态码|短信码|确认码|激活码|邀请码|安全码|otp|passcode|verification code)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_RE = Pattern.compile("^\\s*(\\d{4,8})\\s*$");

    private static final Pattern CRED_RE = Pattern.compile(
            "(账号|帐号|用户名|登录名|密码|口令|username|password|passwd|login|token|secret|api[ _-]?key|access[ _-]?key|密钥|私钥)"
                    + "\\s*[:：=]\\s*([^\\s，。;；]{2,80})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_RE = Pattern.compile(
            "(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{16,}"
                    + "|xox[baprs]-[A-Za-z0-9-]{10,}|AKIA[0-9A-Z]{16}"
                    + "|-----BEGIN [A-Z ]*PRIVATE KEY-----)");

    private static final Pattern MONEY_RE = Pattern.compile(
            "(?:[\\u00A5\\uFFE5$\\u20AC\\u00A3]\\s*[0-9][0-9,.]*"
                    + "|[0-9][0-9,.]*\\s*(?:元|块钱|块|美元|欧元|日元|港币|英镑))");
    private static final Pattern MONEY_WORD_RE = Pattern.compile(
            "(?:价格|金额|总价|售价|优惠|到手价|付款|支付|账单|费用|预算)\\s*[:：]?\\s*"
                    + "([0-9][0-9,.]*(?:\\s*(?:元|块钱|块|美元|欧元))?)");
    private static final Pattern BARE_AMOUNT_RE = Pattern.compile(
            "^\\s*[\\u00A5\\uFFE5$\\u20AC\\u00A3]?\\s*[0-9][0-9,.]*\\s*$");

    private static final Pattern ID_CARD_RE = Pattern.compile("^\\s*(\\d{17}[0-9Xx])\\s*$");
    private static final Pattern ID_CARD_IN_TEXT = Pattern.compile(
            "(?<![0-9])(\\d{17}[0-9Xx])(?![0-9])");
    private static final Pattern BANK_CARD_RE = Pattern.compile(
            "^\\s*((?:\\d{4}[ -]?){3}\\d{4}|(?:\\d{4}[ -]?){4}\\d{3,4}|\\d{16,19})\\s*$");
    private static final Pattern TRACKING_RE = Pattern.compile(
            "^\\s*((?:SF|JD|YT|ZT|DB|EMS|JT|STO|YTO|ZTO|YD|EX)[A-Za-z0-9]{8,})\\s*$");
    private static final Pattern ORDER_RE = Pattern.compile(
            "(订单号|运单号|快递单号|编号|卡号|流水号)\\s*[:：]?\\s*([A-Za-z0-9\\-]{4,40})");

    private static final Pattern ADDR_A = Pattern.compile("[省市自治区特别行政区]");
    private static final Pattern ADDR_B = Pattern.compile("[区县镇乡]");
    private static final Pattern ADDR_C = Pattern.compile(
            "街道|路|街|巷|号|栋|单元|室|小区|大厦|广场|花园|公寓|写字楼|产业园|开发区");

    private static final Set<String> TLDS = new HashSet<>(Arrays.asList(
            "com", "cn", "org", "net", "io", "dev", "app", "ai", "co", "me", "xyz",
            "info", "top", "gov", "edu", "tech", "cloud", "design", "shop", "store",
            "site", "online", "space", "fun", "live", "news", "blog", "link", "mobile",
            "tv", "cc", "hk", "tw", "mo", "uk", "us", "jp", "kr", "de", "fr", "ru",
            "in", "br", "au", "ca", "ch", "sg", "nl", "se", "no", "fi", "es", "it",
            "pt", "pl", "tr", "ae", "sa", "il", "id", "my", "th", "vn", "ph", "nz",
            "za", "mx", "ar", "cl", "at", "be", "cz", "dk", "gr", "hu", "ie", "ro"));

    private static final Set<String> FILE_EXTS = new HashSet<>(Arrays.asList(
            "apk", "aab", "pdf", "png", "jpg", "jpeg", "webp", "gif", "svg", "mp4",
            "mov", "mkv", "avi", "mp3", "wav", "flac", "zip", "rar", "7z", "tar",
            "gz", "bz2", "xz", "md", "txt", "log", "json", "xml", "yaml", "yml",
            "toml", "ini", "csv", "tsv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "html", "htm", "css", "js", "ts", "jsx", "tsx", "java", "kt", "py",
            "go", "rs", "c", "h", "cpp", "hpp", "cs", "php", "rb", "sh", "bat",
            "exe", "dmg", "iso", "epub", "mobi", "ttf", "otf", "woff", "woff2"));

    private static final Set<String> AMBIGUOUS_COMMANDS = new HashSet<>(Arrays.asList(
            "open", "go", "code", "top", "ps", "kill", "make", "service", "net",
            "sc", "find", "cat", "ls", "cd", "du", "df", "mount", "umount",
            "chmod", "chown", "grep", "ping", "source", "alias", "history",
            "export", "defaults"));

    private static final Set<String> COMMANDS = new HashSet<>(Arrays.asList(
            "adb", "git", "npm", "npx", "pnpm", "yarn", "pip", "pip3", "python",
            "python3", "node", "java", "javac", "gradle", "gradlew", "mvn", "docker",
            "kubectl", "curl", "wget", "ssh", "scp", "cd", "ls", "cat", "grep",
            "find", "chmod", "chown", "tar", "unzip", "zip", "brew", "apt",
            "apt-get", "yum", "dnf", "systemctl", "service", "make", "cmake", "gcc",
            "go", "cargo", "rustc", "php", "ruby", "perl", "mysql", "psql", "sqlite3",
            "redis-cli", "ffmpeg", "vim", "nano", "code", "open", "xdg-open", "sh",
            "bash", "zsh", "source", "echo", "export", "alias", "history", "top",
            "ps", "kill", "ping", "traceroute", "dig", "nslookup", "ip", "ifconfig",
            "netstat", "lsof", "du", "df", "mount", "umount", "rsync", "crontab",
            "launchctl", "defaults", "osascript", "powershell", "winget", "choco",
            "scoop", "pod", "composer", "bundle", "rake", "ansible", "terraform",
            "vagrant", "heroku", "gh", "glab", "aws", "gcloud", "az", "eksctl",
            "helm", "minikube", "kind", "skaffold", "cmake", "ninja", "clang",
            "wsl", "taskkill", "sc", "net", "reg", "robocopy", "xcopy"));

    private ClipClassifier() {
    }

    public static Result analyze(String raw) {
        String text = clean(raw);
        if (text.isEmpty()) return null;
        int length = text.length();
        String first = firstLine(text);

        // 1. 验证码：带有“验证码/OTP”等上下文，或本身就是一串六位数字
        String otp = findOtp(text);
        if (otp != null) {
            return new Result(ClipCategory.OTP, "验证码 " + otp, otp, "验证码");
        }

        // 2. 代码优先于链接，避免把含 URL 的代码片段当成链接
        boolean code = looksLikeCode(text);
        if (code) {
            String language = detectLanguage(text);
            int lines = lineCount(text);
            String title = codeTitle(text, language, lines);
            return new Result(ClipCategory.CODE, title, null,
                    language == null ? (lines + " 行") : (language + " · " + lines + " 行"));
        }

        // 3. 终端命令
        if (isCommand(text)) {
            return new Result(ClipCategory.CMD, shorten(first, 34), null, "命令");
        }

        // 4. 链接
        String link = findLink(text);
        if (link != null && (length <= 340 || text.trim().startsWith(link))) {
            return new Result(ClipCategory.LINK, linkTitle(link), link, hostOf(link));
        }

        // 5. 账号 / 密钥（先于联系方式，避免带密码的邮箱被当成普通联系人）
        String secretDetail = accountDetail(text);
        if (secretDetail != null) {
            return new Result(ClipCategory.ACCOUNT, "账号与密钥", null, secretDetail);
        }

        // 6. 邮箱 / 电话
        Matcher email = EMAIL_RE.matcher(text);
        if (email.find() && length <= 220) {
            String value = email.group();
            return new Result(ClipCategory.CONTACT, shorten(value, 32), value, "邮箱");
        }
        String phone = findPhone(text);
        if (phone != null && length <= 90) {
            String kind = phone.startsWith("400") || phone.startsWith("800")
                    ? "客服电话" : (phone.length() == 11 ? "手机号" : "电话");
            return new Result(ClipCategory.CONTACT, phone, phone, kind);
        }

        // 7. 路径 / 文件名（支持“文件已保存到 /sdcard/...”这类句子里的路径）
        String path = findPath(text);
        if (path != null) {
            String base = baseName(path);
            String ext = extensionOf(base);
            return new Result(ClipCategory.PATH, shorten(base, 32), path,
                    ext == null ? "路径" : (ext.toUpperCase(Locale.ROOT) + " 文件"));
        }

        // 8. 金额
        String money = findMoney(text);
        if (money != null) {
            return new Result(ClipCategory.MONEY, money, money, "金额");
        }

        // 9. 地址
        if (isAddress(text)) {
            return new Result(ClipCategory.ADDRESS, shorten(first.replace('\n', ' '), 26), text, "地址");
        }

        // 10. 证件号 / 卡号 / 快递单号 / 普通数字
        NumberHit number = findNumberDoc(text);
        if (number != null) {
            return new Result(ClipCategory.NUMBER, shorten(number.value, 32), number.value, number.kind);
        }

        // 11. 长文
        int newlines = countChar(text, '\n');
        if (length >= 45 || newlines >= 2) {
            String title = shorten(firstSentence(text), 30);
            return new Result(ClipCategory.NOTE, title, null, "长文 · " + length + " 字");
        }

        // 12. 短句
        String title = shorten(first.replace('\n', ' '), 32);
        String detail = length > 40 ? (length + " 字") : null;
        return new Result(ClipCategory.TEXT, title, null, detail);
    }

    public static String clean(String raw) {
        if (raw == null) return "";
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace("\u0000", "");
        return text.trim();
    }

    public static String shorten(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    private static String firstLine(String text) {
        int start = 0;
        int length = text.length();
        while (start < length) {
            int end = text.indexOf('\n', start);
            String line = (end < 0 ? text.substring(start) : text.substring(start, end)).trim();
            if (!line.isEmpty()) return line;
            if (end < 0) break;
            start = end + 1;
        }
        return text.trim();
    }

    private static String firstSentence(String text) {
        String line = firstLine(text);
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                if (i >= 8) return line.substring(0, i + 1);
            }
        }
        return line;
    }

    private static int lineCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') count++;
        return Math.max(1, count + 1);
    }

    private static int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == c) count++;
        return count;
    }

    private static String findOtp(String text) {
        boolean url = text.contains("://");
        if (!url || STRONG_OTP_RE.matcher(text).find()) {
            Matcher context = OTP_CONTEXT_RE.matcher(text);
            if (context.find()) {
                String candidate = context.group(2);
                if (candidate.length() >= 4 && candidate.length() <= 8) {
                    return candidate.toUpperCase(Locale.ROOT);
                }
            }
        }
        Matcher six = SIX_DIGITS_RE.matcher(text);
        if (six.matches()) return six.group(1);
        return null;
    }

    private static String findLink(String text) {
        String trimmed = text.trim();
        Matcher url = URL_RE.matcher(trimmed);
        if (url.find()) return trimUrl(url.group());
        Matcher www = WWW_RE.matcher(trimmed);
        if (www.find()) {
            String value = trimUrl(www.group());
            return "https://" + value;
        }
        Matcher bare = BARE_LINK_RE.matcher(trimmed);
        if (bare.matches()) {
            String host = bare.group(1).toLowerCase(Locale.ROOT);
            String tld = host.substring(host.lastIndexOf('.') + 1);
            if (tld.length() == 2 || TLDS.contains(tld)) {
                if (!FILE_EXTS.contains(tld)) return "https://" + trimmed;
            }
        }
        return null;
    }

    private static String trimUrl(String value) {
        int end = value.length();
        while (end > 1) {
            char c = value.charAt(end - 1);
            if (".,;:!?)]}，。；：！？）】、".indexOf(c) < 0) break;
            end--;
        }
        return value.substring(0, end);
    }

    private static String hostOf(String url) {
        try {
            String value = url;
            if (value.startsWith("https://")) value = value.substring(8);
            else if (value.startsWith("http://")) value = value.substring(7);
            else if (value.startsWith("ftp://")) value = value.substring(6);
            int slash = value.indexOf('/');
            String host = slash < 0 ? value : value.substring(0, slash);
            int port = host.indexOf(':');
            if (port >= 0) host = host.substring(0, port);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception error) {
            return url;
        }
    }

    private static String linkTitle(String url) {
        String host = hostOf(url);
        String path = "";
        try {
            String value = url;
            int scheme = value.indexOf("://");
            if (scheme >= 0) value = value.substring(scheme + 3);
            int slash = value.indexOf('/');
            if (slash >= 0) {
                path = value.substring(slash);
                int query = path.indexOf('?');
                if (query >= 0) path = path.substring(0, query);
                int hash = path.indexOf('#');
                if (hash >= 0) path = path.substring(0, hash);
            }
        } catch (Exception ignored) {
        }
        String title = host + (path.isEmpty() ? "" : path);
        return shorten(title, 38);
    }

    private static String accountDetail(String text) {
        if (text.length() > 1400) return null;
        Matcher token = TOKEN_RE.matcher(text);
        if (token.find()) {
            String value = token.group();
            if (value.startsWith("-----BEGIN")) return "私钥";
            if (value.startsWith("ghp_") || value.startsWith("github_pat_")) return "GitHub 令牌";
            if (value.startsWith("sk-")) return "API 令牌";
            if (value.startsWith("xox")) return "Slack 令牌";
            if (value.startsWith("AKIA")) return "AWS 密钥";
            return "密钥";
        }
        Matcher cred = CRED_RE.matcher(text);
        int matches = 0;
        boolean password = false;
        boolean account = false;
        while (cred.find()) {
            matches++;
            String key = cred.group(1).toLowerCase(Locale.ROOT);
            if (key.contains("密码") || key.contains("口令") || key.contains("password")
                    || key.contains("passwd")) {
                password = true;
            } else {
                account = true;
            }
        }
        if (matches == 0) return null;
        if (!password && !account) return "凭据";
        if (password && account) return "账号 + 密码";
        return password ? "密码" : "账号";
    }

    private static String findPhone(String text) {
        Matcher mobile = MOBILE_RE.matcher(text);
        if (mobile.find()) return digits(mobile.group());
        Matcher service = SERVICE_RE.matcher(text);
        if (service.find()) return digits(service.group());
        Matcher landline = LANDLINE_RE.matcher(text);
        if (landline.find()) return digits(landline.group());
        return null;
    }

    private static String digits(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= '0' && c <= '9') || c == '+') builder.append(c);
        }
        return builder.toString();
    }

    private static boolean looksLikeCode(String text) {
        String trimmed = text.trim();
        if (countChar(trimmed, '\n') == 0
                && trimmed.matches("(?i)^(https?|ftp)://.*")) return false;
        int newlines = countChar(trimmed, '\n');
        if (newlines >= 1) {
            boolean braces = trimmed.indexOf('{') >= 0 && trimmed.indexOf('}') >= 0;
            boolean semicolons = trimmed.contains(";\n") || trimmed.endsWith(";");
            if (braces && (trimmed.contains("\"") || trimmed.contains("'")
                    || trimmed.contains("=") || trimmed.contains("=>"))) return true;
            if (semicolons && (trimmed.contains("(") || trimmed.contains("="))) return true;
            if (trimmed.contains("=>") || trimmed.contains("</")
                    || trimmed.contains("#include") || trimmed.contains("<?php")) return true;
            if (trimmed.startsWith("<") && trimmed.contains(">")
                    && (trimmed.contains("</") || trimmed.contains("/>"))) return true;
            if (trimmed.contains("SELECT ") && trimmed.contains(" FROM ")) return true;
            if (trimmed.contains("def ") || trimmed.contains("function ")
                    || trimmed.contains("class ") || trimmed.contains("import ")
                    || trimmed.contains("package ") || trimmed.contains("public ")
                    || trimmed.contains("private ") || trimmed.contains("func ")) {
                if (trimmed.contains("(") || trimmed.contains(":")
                        || trimmed.contains("{") || trimmed.contains("=")) return true;
            }
            if (trimmed.contains("console.log(") || trimmed.contains("System.out")
                    || trimmed.contains("printf(") || trimmed.contains("print(")) return true;
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            boolean ends = trimmed.endsWith("}") || trimmed.endsWith("]");
            if (ends && trimmed.contains(":") && (trimmed.contains("\"") || trimmed.contains("'"))) {
                return true;
            }
        }
        if (trimmed.endsWith(";") && trimmed.length() >= 8 && asciiRatio(trimmed) > 0.55) return true;
        if (trimmed.contains("SELECT ") && trimmed.contains(" FROM ")
                && asciiRatio(trimmed) > 0.55) return true;
        if (trimmed.contains("() {") || trimmed.contains("(){") || trimmed.contains("=>")
                || trimmed.contains(" === ") || trimmed.contains(" !== ")) {
            return asciiRatio(trimmed) > 0.45;
        }
        if (trimmed.matches(".*\\b[a-zA-Z_][a-zA-Z0-9_.]*\\.[a-zA-Z_][a-zA-Z0-9_]*\\(.*\\).*")
                && asciiRatio(trimmed) > 0.6) {
            return true;
        }
        return false;
    }

    private static double asciiRatio(String text) {
        if (text.isEmpty()) return 0;
        int ascii = 0;
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            if (c < 128 && c != ' ' && c != '\n' && c != '\t') ascii++;
        }
        return (double) ascii / text.length();
    }

    private static String detectLanguage(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String trimmed = text.trim();
        if ((trimmed.startsWith("{") || trimmed.startsWith("["))
                && (trimmed.endsWith("}") || trimmed.endsWith("]"))
                && trimmed.contains(":")
                && (trimmed.contains("\"") || trimmed.contains("'"))) return "JSON";
        if (lower.contains("public class ") || lower.contains("import java.")
                || lower.contains("system.out") || lower.contains("private static")) return "Java";
        if (lower.contains("fun main") || lower.contains("val ") && lower.contains("var ")) return "Kotlin";
        if (lower.contains("function ") || lower.contains("console.log")
                || lower.contains("=>") || lower.contains("const ")
                || lower.contains("let ") || lower.contains("document.")) return "JavaScript";
        if (lower.contains("def ") || lower.contains("import ") && lower.contains(":")
                || lower.contains("print(") || lower.contains("self.")) return "Python";
        if (lower.contains("<?php")) return "PHP";
        if (lower.contains("#include") || lower.contains("std::") || lower.contains("int main(")) return "C/C++";
        if (lower.contains("func ") && lower.contains("package ")) return "Go";
        if (lower.contains("fn ") && lower.contains("let mut")) return "Rust";
        if (lower.contains("select ") && lower.contains(" from ")) return "SQL";
        if (trimmed.startsWith("<") && lower.contains("</")) return "HTML";
        if (lower.contains("class ") && lower.contains(":") && !lower.contains("{")) return "Python";
        if (lower.contains("$ ") || trimmed.startsWith("#!")) return "Shell";
        if (lower.contains("package ") && lower.contains("import ")) return "Java";
        return null;
    }

    private static String codeTitle(String text, String language, int lines) {
        String first = firstLine(text);
        if (first.length() > 36) first = shorten(first, 36);
        if (first.equals("{") || first.equals("[") || first.isEmpty()) {
            return (language == null ? "代码" : language) + " · " + lines + " 行";
        }
        if (language != null && first.length() < 5) {
            return language + " · " + lines + " 行";
        }
        return first;
    }

    private static boolean isCommand(String text) {
        String first = firstLine(text).trim();
        if (first.isEmpty()) return false;
        String stripped = first;
        if (stripped.startsWith("$ ") || stripped.startsWith("> ")) stripped = stripped.substring(2).trim();
        if (stripped.startsWith("#!")) return true;
        String token = stripped.split("\\s+")[0];
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/")) lower = lower.substring(0, lower.length() - 1);
        if (COMMANDS.contains(lower)) {
            if (AMBIGUOUS_COMMANDS.contains(lower)) {
                String rest = stripped.substring(token.length()).trim();
                if (!rest.isEmpty() && !cliLooking(rest)) return false;
            }
            return true;
        }
        if (first.contains(" && ") || first.contains(" || ")
                || (first.contains(" | ") && first.split("\\|").length >= 2)) {
            String head = first.split("[|&]")[0].trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            return COMMANDS.contains(head);
        }
        return false;
    }

    private static boolean cliLooking(String rest) {
        if (rest.contains("/") || rest.contains("\\") || rest.contains(".")
                || rest.contains("=") || rest.contains("://") || rest.contains("@")
                || rest.contains("~/") || rest.contains(" -") || rest.contains("--")) {
            return true;
        }
        return rest.matches("^[0-9 ,]+$");
    }

    /** 返回文本中的路径（整段就是路径，或中文句子里夹着的一条路径）。 */
    private static String findPath(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 700) return null;
        if (isWholePath(trimmed)) return trimmed;
        if (trimmed.length() > 260) return null;
        if (trimmed.contains("://")) return null;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean start = false;
            if (c == '/' && i > 0) {
                char prev = trimmed.charAt(i - 1);
                if (prev == ':' || prev == '/') continue;
                start = isSeparatorOrStart(prev);
            } else if (c == '~' && i + 1 < trimmed.length() && trimmed.charAt(i + 1) == '/') {
                start = i == 0 || isSeparatorOrStart(trimmed.charAt(i - 1));
            } else if (c == '.' && i + 1 < trimmed.length()
                    && (trimmed.charAt(i + 1) == '/' || trimmed.charAt(i + 1) == '.')) {
                start = i == 0 || isSeparatorOrStart(trimmed.charAt(i - 1));
            } else if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
                if (i + 2 < trimmed.length() && trimmed.charAt(i + 1) == ':'
                        && (trimmed.charAt(i + 2) == '\\' || trimmed.charAt(i + 2) == '/')) {
                    start = i == 0 || isSeparatorOrStart(trimmed.charAt(i - 1));
                    if (!start) continue;
                    if (i + 2 >= trimmed.length()) continue;
                    int end = scanTokenEnd(trimmed, i);
                    String token = trimmed.substring(i, end).trim();
                    if (token.length() > 2) return token;
                    continue;
                }
                continue;
            }
            if (!start) continue;
            int from = c == '/' && i > 0 ? i : i;
            // 从斜杠本身开始取（~ 和 ./ 也包含前缀）
            int end = scanTokenEnd(trimmed, from);
            String token = trimmed.substring(from, end).trim();
            token = trimPathPunctuation(token);
            if (token.length() > 1 && looksLikePathToken(token)) return token;
        }
        return null;
    }

    private static boolean isSeparatorOrStart(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '（' || c == '('
                || c == '：' || c == ':' || c == '，' || c == ',' || c == '。'
                || c == '；' || c == ';' || c == '“' || c == '「' || c == '【';
    }

    private static int scanTokenEnd(String text, int from) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t' || c == '，' || c == '。'
                    || c == '；' || c == '：' || c == '！' || c == '？'
                    || c == '、' || c == '）' || c == '】' || c == '」'
                    || c == '”' || c == '\'' || c == '"') break;
            i++;
        }
        return i;
    }

    private static String trimPathPunctuation(String value) {
        String token = value.trim();
        while (token.length() > 1) {
            char c = token.charAt(token.length() - 1);
            if (",.;:!?)]}，。；：！？）】、".indexOf(c) < 0) break;
            token = token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static boolean looksLikePathToken(String token) {
        if (token.startsWith("/") || token.startsWith("~/") || token.startsWith("./")
                || token.startsWith("../") || token.matches("^[A-Za-z]:\\\\.*")) {
            if (token.equals("/")) return false;
            String name = baseName(token);
            String ext = extensionOf(name);
            if (ext != null && FILE_EXTS.contains(ext)) return true;
            if (token.startsWith("/") && token.matches("^/[A-Za-z0-9_\\-./]+$")) return true;
            return false;
        }
        return false;
    }

    private static boolean isWholePath(String trimmed) {
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\t') >= 0) return false;
        if (trimmed.contains("://") || trimmed.startsWith("www.")) return false;
        if (trimmed.startsWith("/") && trimmed.length() > 1) {
            return !trimmed.contains(" ") || trimmed.matches("^/[^ ]*$");
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("./") || trimmed.startsWith("../")) {
            return !trimmed.contains(" ");
        }
        if (trimmed.matches("^[A-Za-z]:\\\\.*")) return true;
        if (trimmed.contains(" ")) return false;
        String ext = extensionOf(trimmed);
        if (ext != null && FILE_EXTS.contains(ext)) return true;
        return false;
    }

    private static String baseName(String path) {
        String value = path.trim();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.isEmpty() ? value : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() < 2 || ext.length() > 5) return null;
        if (!ext.matches("[a-z0-9]+")) return null;
        return ext;
    }

    private static String findMoney(String text) {
        String trimmed = text.trim();
        if (trimmed.length() > 90) return null;
        Matcher symbol = MONEY_RE.matcher(trimmed);
        if (symbol.find()) return symbol.group().replaceAll("\\s+", "");
        Matcher word = MONEY_WORD_RE.matcher(trimmed);
        if (word.find()) return word.group(1).replaceAll("\\s+", "");
        return null;
    }

    private static boolean isAddress(String text) {
        String trimmed = text.trim().replace('\n', ' ');
        if (trimmed.length() < 6 || trimmed.length() > 180) return false;
        if (trimmed.indexOf('。') >= 0 || trimmed.indexOf('！') >= 0 || trimmed.indexOf('？') >= 0) {
            if (trimmed.length() > 45) return false;
        }
        int groups = 0;
        if (ADDR_A.matcher(trimmed).find()) groups++;
        if (ADDR_B.matcher(trimmed).find()) groups++;
        int cCount = 0;
        Matcher c = ADDR_C.matcher(trimmed);
        while (c.find()) cCount++;
        if (cCount >= 2) groups += 2;
        else if (cCount == 1) groups++;
        if (trimmed.startsWith("中国")) groups++;
        if (trimmed.contains("地址") && cCount >= 1) groups++;
        return groups >= 3;
    }

    private static final class NumberHit {
        final String value;
        final String kind;

        NumberHit(String value, String kind) {
            this.value = value;
            this.kind = kind;
        }
    }

    private static NumberHit findNumberDoc(String text) {
        String trimmed = text.trim();
        Matcher id = ID_CARD_RE.matcher(trimmed);
        if (id.matches()) return new NumberHit(id.group(1), "身份证号");
        if (trimmed.length() <= 90 && (trimmed.contains("身份证") || trimmed.contains("证件"))) {
            Matcher idInText = ID_CARD_IN_TEXT.matcher(trimmed);
            if (idInText.find()) return new NumberHit(idInText.group(1), "身份证号");
        }
        Matcher bank = BANK_CARD_RE.matcher(trimmed);
        if (bank.matches()) {
            String value = digits(bank.group(1));
            if (value.length() >= 16 && value.length() <= 19) {
                return new NumberHit(value, "银行卡号");
            }
        }
        Matcher tracking = TRACKING_RE.matcher(trimmed);
        if (tracking.matches()) return new NumberHit(tracking.group(1).toUpperCase(Locale.ROOT), "快递单号");
        Matcher order = ORDER_RE.matcher(trimmed);
        if (order.find() && trimmed.length() <= 80) {
            return new NumberHit(order.group(2), order.group(1));
        }
        Matcher digits = DIGITS_RE.matcher(trimmed);
        if (digits.matches()) return new NumberHit(digits.group(1), "数字");
        if (BARE_AMOUNT_RE.matcher(trimmed).matches() && trimmed.length() <= 16) {
            return new NumberHit(trimmed, "数字");
        }
        return null;
    }
}
