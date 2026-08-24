# StitchCraft 1.0 RC8 — Google Play Test Candidate

Основано на проверенной RC7 FIX3 Anchored Chart.

## Зафиксировано как рабочее
- генерация схем из изображения;
- DMC-палитра;
- Aida и физический размер готовой вышивки;
- отметка крестиков и прогресс;
- Undo / Redo;
- zoom двумя пальцами;
- прокрутка страницы одним пальцем без ухода схемы из окна;
- сохранение, переименование, удаление, импорт и экспорт проектов;
- PDF / PNG / CSV;
- Pro-экран и восстановление покупки;
- «Материалы»: поиск канвы Aida и отдельных цветов DMC во внешних магазинах;
- Privacy Policy и адрес поддержки.

## Версия
- package: `com.stitchcraft.app`
- versionCode: 112
- versionName: `1.0.0-rc8-play-test`
- targetSdk: 36

## Google Play
Перед закрытым тестированием нужно:
1. Создать upload key и добавить его в GitHub Secrets.
2. Получить артефакт `StitchCraft-google-play-aab` из GitHub Actions.
3. Создать разовую покупку `stitchcraft_pro_lifetime` в Play Console.
4. Загрузить signed AAB в закрытое тестирование.
5. Добавить минимум 12 тестировщиков и провести тест минимум 14 дней (если это требование показано вашим аккаунтом Play Console).
