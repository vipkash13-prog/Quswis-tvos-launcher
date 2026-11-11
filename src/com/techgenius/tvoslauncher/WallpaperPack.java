package com.techgenius.tvoslauncher;

import android.content.Context;
import java.util.*;

/** Additive UHD artwork collection. Existing scene IDs and wallpaper keys are unchanged. */
final class WallpaperPack {
    static final String[][] ITEMS = {
        {"night-lake", "بحيرة ليلية — 4K", "Night lake — 4K"},
        {"night-coast", "ساحل ليلي — 4K", "Moonlit coast — 4K"},
        {"classic-alpine", "بحيرة الألب — كلاسيك 4K", "Alpine lake — Classic 4K"},
        {"classic-coast", "الساحل الذهبي — كلاسيك 4K", "Golden coast — Classic 4K"},
        {"saudi-alula", "السعودية — العلا 4K", "Saudi Arabia — AlUla 4K"},
        {"saudi-riyadh", "السعودية — الرياض 4K", "Saudi Arabia — Riyadh 4K"},
        {"morocco-chefchaouen", "المغرب — شفشاون 4K", "Morocco — Chefchaouen 4K"},
        {"morocco-atlas", "المغرب — الأطلس 4K", "Morocco — Atlas 4K"},
        {"riyadh-golden-night", "الرياض — ليل ذهبي 4K", "Riyadh — Golden night 4K"}
    };
    static String asset(String id) { return "backgrounds/uhd/" + id + ".webp"; }
    static String title(Context c, String id) {
        for (String[] item : ITEMS) if (id.equals("uhd_" + item[0])) return "ar".equals(I18n.getLang(c)) ? item[1] : item[2];
        return null;
    }
    static Scenes.Spec[] scenes(Scenes.Spec[] old) {
        List<Scenes.Spec> out = new ArrayList<>(Arrays.asList(old));
        for (String[] item : ITEMS) out.add(new Scenes.Spec("uhd_" + item[0], item[2], item[2],
                asset(item[0]), asset(item[0]), null, false, false, false, 68f, 190f, 0f, 45f, 0f, false));
        return out.toArray(new Scenes.Spec[0]);
    }
    static String[][] classics(String[][] old) {
        List<String[]> out = new ArrayList<>(Arrays.asList(old));
        for (String[] item : ITEMS) out.add(new String[]{"uhd/" + item[0] + ".webp", item[1], item[2]});
        return out.toArray(new String[0][]);
    }
}
