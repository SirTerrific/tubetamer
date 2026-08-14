"""Tests for locale and time formatting helpers."""

from i18n import app_name, format_time, format_time_compact, normalize_time_format


class TestAppName:
    def test_defaults_to_TubeTamer_in_english(self):
        assert app_name("en") == "TubeTamer"

    def test_uses_tubetamer_in_norwegian(self):
        assert app_name("nb") == "TubeTamer"


class TestNormalizeTimeFormat:
    def test_defaults_to_locale(self):
        assert normalize_time_format(None) == "locale"
        assert normalize_time_format("garbage") == "locale"

    def test_normalizes_aliases(self):
        assert normalize_time_format("24hour") == "24h"
        assert normalize_time_format("12hr") == "12h"


class TestFormatTime:
    def test_en_locale_defaults_to_12h(self):
        assert format_time("20:00", "en") == "8 PM"

    def test_nb_locale_defaults_to_24h(self):
        assert format_time("20:00", "nb") == "20:00"

    def test_forced_24h_overrides_locale(self):
        assert format_time("20:00", "en", time_format="24h") == "20:00"

    def test_forced_12h_overrides_locale(self):
        assert format_time("20:00", "nb", time_format="12h") == "8 PM"


class TestFormatTimeCompact:
    def test_compact_12h(self):
        assert format_time_compact("20:00", "en") == "8p"

    def test_compact_24h(self):
        assert format_time_compact("20:00", "en", time_format="24h") == "20"


class TestFrenchLocale:
    def test_app_name_stays_untranslated(self):
        assert app_name("fr") == "TubeTamer"

    def test_locale_variants_normalize_to_fr(self):
        from i18n import normalize_locale
        for variant in ("fr", "FR", "fr-FR", "fr_CA", "fr-ca"):
            assert normalize_locale(variant) == "fr"

    def test_translates_a_known_key(self):
        from i18n import t
        assert t("fr", "Search") != "Search"

    def test_unknown_key_falls_back_to_key(self):
        from i18n import t
        assert t("fr", "__no_such_key__") == "__no_such_key__"

    def test_uses_24h_time(self):
        assert format_time("14:30", "fr") == "14:30"

    def test_placeholders_survive_formatting(self):
        from i18n import t
        assert "Alice" in t("fr", "Hi {name}!", name="Alice")
