# StitchCraft v0.6 — статический аудит

Исправлено:
- устранено двойное объявление ThreadColor, которое могло ломать компиляцию;
- версия приложения синхронизирована с v0.6;
- Billing product id теперь берётся из единого ReleaseConfig;
- backup/data extraction XML подключены к AndroidManifest;
- Free/Pro лимиты генерации берутся из ReleaseConfig вместо разрозненных чисел;
- ProjectStore получил fallback на новый DMC repository.

Ограничение проверки:
В этой рабочей среде нет установленного Android Gradle/SDK toolchain, поэтому реальная компиляция APK/AAB здесь не подтверждена.
Финальная проверка сборки должна быть выполнена в Android Studio/Gradle на машине с Android SDK.

Следующая цель:
- тестовая сборка;
- устранение compile/runtime ошибок, если Android Studio их покажет;
- затем release signing и Internal Testing Google Play.
