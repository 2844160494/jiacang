package dev.local.globalclip;

import java.util.ArrayList;
import java.util.List;

/** 分类表：纯数据，界面和识别器共用。 */
public final class ClipCategory {
    public static final String ALL = "all";
    public static final String OTP = "otp";
    public static final String LINK = "link";
    public static final String CONTACT = "contact";
    public static final String ACCOUNT = "account";
    public static final String CODE = "code";
    public static final String CMD = "cmd";
    public static final String PATH = "path";
    public static final String MONEY = "money";
    public static final String ADDRESS = "address";
    public static final String NUMBER = "number";
    public static final String NOTE = "note";
    public static final String TEXT = "text";

    public static final String[] ORDER = {
            OTP, LINK, CONTACT, ACCOUNT, CODE, CMD, PATH, MONEY, ADDRESS, NUMBER, NOTE, TEXT
    };

    private ClipCategory() {
    }

    public static String label(String key) {
        if (key == null) return "其他";
        switch (key) {
            case OTP: return "验证码";
            case LINK: return "链接";
            case CONTACT: return "联系方式";
            case ACCOUNT: return "账号密钥";
            case CODE: return "代码";
            case CMD: return "命令";
            case PATH: return "路径";
            case MONEY: return "金额";
            case ADDRESS: return "地址";
            case NUMBER: return "号码";
            case NOTE: return "长文";
            case TEXT: return "短句";
            default: return "其他";
        }
    }

    public static String glyph(String key) {
        if (key == null) return "句";
        switch (key) {
            case OTP: return "验";
            case LINK: return "链";
            case CONTACT: return "联";
            case ACCOUNT: return "密";
            case CODE: return "码";
            case CMD: return "令";
            case PATH: return "径";
            case MONEY: return "金";
            case ADDRESS: return "址";
            case NUMBER: return "号";
            case NOTE: return "文";
            case TEXT: return "句";
            default: return "句";
        }
    }

    /** 分类在界面里的强调色（与 Ui 调色板一致的墨色系）。 */
    public static int color(String key) {
        if (key == null) return 0xFF6B7280;
        switch (key) {
            case OTP: return 0xFFB7791F;
            case LINK: return 0xFF2B6CB0;
            case CONTACT: return 0xFF0F766E;
            case ACCOUNT: return 0xFF9A3412;
            case CODE: return 0xFF6D28D9;
            case CMD: return 0xFF475569;
            case PATH: return 0xFF8A5A2B;
            case MONEY: return 0xFF2F7D32;
            case ADDRESS: return 0xFFC05621;
            case NUMBER: return 0xFF9D174D;
            case NOTE: return 0xFF3F5E7A;
            case TEXT: return 0xFF6B7280;
            default: return 0xFF6B7280;
        }
    }

    public static int tint(String key) {
        int color = color(key);
        return (color & 0x00FFFFFF) | 0x1A000000;
    }

    public static List<String> present() {
        List<String> keys = new ArrayList<>();
        for (String key : ORDER) keys.add(key);
        return keys;
    }
}
