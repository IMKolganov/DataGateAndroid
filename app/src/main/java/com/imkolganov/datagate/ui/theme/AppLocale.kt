package com.imkolganov.datagate.ui.theme

import java.util.Locale

/**
 * App UI language. [SYSTEM] follows the device locale (default). Other values pin a specific locale.
 * [pickerOrder] is how languages appear in Settings: system → English → Russian → Ukrainian →
 * European → rest. [languageTag] is BCP‑47.
 */
enum class AppLocale(val languageTag: String?) {
    SYSTEM(null),

    EN("en"),
    BG("bg"),
    HR("hr"),
    CS("cs"),
    DA("da"),
    NL("nl"),
    ET("et"),
    FI("fi"),
    FR("fr"),
    DE("de"),
    EL("el"),
    HU("hu"),
    GA("ga"),
    IT("it"),
    LV("lv"),
    LT("lt"),
    MT("mt"),
    PL("pl"),
    PT("pt"),
    RO("ro"),
    SK("sk"),
    SL("sl"),
    ES("es"),
    SV("sv"),

    RU("ru"),
    /** Ukrainian (right after Russian in the picker). */
    UK("uk"),

    /** Persian (Iran) */
    FA_IR("fa-IR"),
    TR("tr"),
    /** Hindi (India) */
    HI_IN("hi-IN"),
    /** Chinese (Simplified, mainland) */
    ZH_CN("zh-CN"),
    /** Chinese (Traditional) */
    ZH_TW("zh-TW"),
    /** Spanish (Mexico) */
    ES_MX("es-MX"),
    /** Arabic (generic MS Arabic; broad regional fallback) */
    AR("ar"),
    JA("ja"),
    KO("ko"),
    PT_BR("pt-BR"),
    VI("vi"),
    TH("th"),
    ID("id"),
    FIL("fil");

    fun displayLabel(uiLocale: Locale): String {
        if (this == SYSTEM) return "" // caller uses string resource
        val tag = languageTag ?: return ""
        return Locale.forLanguageTag(tag).getDisplayName(uiLocale)
    }

    companion object {
        val pinnedValues: List<AppLocale> = values().filter { it != SYSTEM }

        private val europeanLocales: List<AppLocale> = listOf(
            BG, HR, CS, DA, NL, ET, FI, FR, DE, EL, HU, GA, IT, LV, LT, MT, PL, PT, RO, SK, SL, ES, SV
        )

        private val nonEuropeanLocales: List<AppLocale> = listOf(
            FA_IR, TR, HI_IN, ZH_CN, ZH_TW, ES_MX, AR, JA, KO, PT_BR, VI, TH, ID, FIL
        )

        /**
         * Order for the language dropdown: system default, English, Russian, Ukrainian,
         * then European locales, then all other pinned locales.
         */
        val pickerOrder: List<AppLocale> = buildList {
            add(SYSTEM)
            add(EN)
            add(RU)
            add(UK)
            addAll(europeanLocales)
            addAll(nonEuropeanLocales)
        }
    }
}
