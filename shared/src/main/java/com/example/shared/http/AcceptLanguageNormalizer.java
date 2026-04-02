package com.example.shared.http;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normalizes browser-style Accept-Language headers into a supported primary language tag. */
public final class AcceptLanguageNormalizer {

  private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "zh", "ja");
  private static final String DEFAULT_LANGUAGE = "en";

  private AcceptLanguageNormalizer() {}

  public static String normalize(String acceptLanguageHeader) {
    if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
      return DEFAULT_LANGUAGE;
    }

    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguageHeader);
      for (Locale.LanguageRange range : ranges) {
        String normalized = normalizeRange(range.getRange());
        if (normalized != null) {
          return normalized;
        }
      }
    } catch (IllegalArgumentException ignored) {
      String normalized = normalizeRange(acceptLanguageHeader);
      if (normalized != null) {
        return normalized;
      }
    }

    return DEFAULT_LANGUAGE;
  }

  private static String normalizeRange(String languageRange) {
    if (languageRange == null || languageRange.isBlank() || "*".equals(languageRange)) {
      return null;
    }

    String primaryLanguage =
        Locale.forLanguageTag(languageRange.trim()).getLanguage().toLowerCase(Locale.ROOT);
    if (SUPPORTED_LANGUAGES.contains(primaryLanguage)) {
      return primaryLanguage;
    }

    return null;
  }
}
