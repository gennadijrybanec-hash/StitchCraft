## v0.15.2
- Fixed stale tap callback in PatternCanvas that caused only the latest completed stitch to remain.
- Fixed pan offset resetting on every pattern edit, which made the visible check mark appear to jump.
- Viewport now stays stable while marking stitches.

# v0.12.0

- Более отзывчивый pinch-to-zoom для схемы.
- Шаг кнопок масштаба увеличен до ×2 / ÷2.
- Генератор и экспорт оставлены без изменений.


## v0.11
- Cleaner photo downsampling for stitch grids.
- Two-pass conservative isolated-stitch cleanup with CIEDE2000 guard.
- Better preservation of edges and thin details while reducing color speckles.
# v0.7 — Phone Build / GitHub Actions
- добавлен облачный workflow Build StitchCraft APK;
- добавлен отдельный compile/lint workflow;
- GitHub Actions использует JDK 17 и Gradle 8.9;
- готовый debug APK публикуется как artifact на 14 дней;
- добавлена пошаговая инструкция сборки полностью со смартфона;
- versionCode/versionName обновлены до 7 / 0.7.0.

# v0.6 — build hardening
- исправлено конфликтующее двойное объявление ThreadColor;
- Billing product id централизован через ReleaseConfig;
- Android backup/data extraction rules подключены в Manifest;
- versionCode/versionName обновлены до 6 / 0.6.0;
- Free/Pro лимиты UI и генератора синхронизированы через ReleaseConfig;
- добавлен статический аудит готовности к сборке.

# v0.5 — release readiness
- добавлен ReleaseConfig с Free/Pro лимитами и product id;
- добавлена безопасная схема именования экспортов;
- добавлены Android backup/data-extraction rules;
- добавлены Google Play Data Safety и Android build checklists;
- зафиксировано: analytics/cloud/crash reporting выключены до осознанного подключения;
- обновлён privacy draft под текущую архитектуру.

# v0.4 — коммерческая подготовка
- изолирован репозиторий палитры ниток;
- добавлена стартовая DMC-палитра для разработки (не финальная полная база);
- добавлено постоянное хранение пользовательских настроек;
- добавлены черновики Privacy Policy и страницы поддержки;
- подготовлена архитектура для финальной палитры и релизной полировки.

# StitchCraft — changelog

## 0.2.0-commercial-base
- Перцептивное сопоставление цветов в пространстве CIELAB.
- Метрика CIEDE2000 для выбора ближайшего оттенка нити.
- Улучшенный жадный выбор палитры по снижению визуальной ошибки.
- Опциональная очистка одиночных крестиков/цветового шума.
- Автоматическое удаление неиспользованных цветов из итоговой палитры.
- Сохранены Free/Pro ограничения и существующие PDF/PNG/CSV экспорты.

## Следующий этап
- Ручное редактирование клеток.
- Undo/Redo.
- Отметка вышитых крестиков и прогресс проекта.
- Разбиение больших PDF-схем на печатные страницы с координатами и легендой.

## 0.8.0
- Improved chart editor readability: symbols automatically switch between black and white for contrast.
- Centered and boldened chart symbols at useful zoom levels.
- Added editor zoom controls: minus, plus, and "По размеру" to reset zoom/pan.
- Unified Free/Pro limits shown in the UI with ReleaseConfig values.
- Generation now uses the same Free/Pro limits as the UI.
- Updated GitHub Actions checkout/setup-java to v5.

## 0.9.0
- Increased editor zoom limit to 20x.
- Added visible zoom percentage and stronger 10x10 guide grid.
- Added bounded panning for enlarged patterns.
- Added a stable test-only debug signing key for reproducible GitHub Actions updates.
- Bumped Android versionCode/versionName to 9 / 0.9.0.

## 0.14.0
- Portable project export/import (.stitchcraft).
- Stronger persistent completed-stitch visualization.

## 1.0.0-rc1
- Комплексный предрелизный аудит на базе v0.15.2.
- Исправлены лимиты цветов Pro и защита генератора.
- Усилена работа с большими изображениями и PNG.
- PDF получил полную легенду палитры.
- Усилена валидация `.stitchcraft`.
- Улучшено открытие проекта из Android.
- Добавлены иконка, unit-тесты и lint в CI.

## 1.0.0-rc2
- Brand launcher icon and splash branding.
- Responsive project action row for portrait screens.
- Refined Pro presentation.
