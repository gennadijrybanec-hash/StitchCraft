# StitchCraft 1.0 RC5 — подготовка Google Play

RC5 построен на проверенной RC4.1 Commercial Clean. Генератор схем, проекты, прогресс, Aida и экспорт не переделывались.

Изменения RC5:
- targetSdk и compileSdk подняты до API 36;
- Android Gradle Plugin обновлён до 8.11.1;
- GitHub Actions использует Gradle 8.13;
- добавлена сборка Release AAB;
- добавлена безопасная подпись Google Play AAB через GitHub Secrets;
- versionCode = 105;
- versionName = 1.0.0-rc5.

Google Play Pro product ID уже зафиксирован: `stitchcraft_pro_lifetime`.

До создания upload key workflow всегда выдаёт Debug APK и unsigned Release AAB. После добавления четырёх секретов он дополнительно выдаст `StitchCraft-google-play-aab`.
