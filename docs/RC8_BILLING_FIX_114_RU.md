# StitchCraft RC8 Billing Fix — versionCode 114

Изменения:
- Google Play Billing больше не использует сохранённый ProductDetails перед покупкой: товар запрашивается заново при каждом нажатии «Получить StitchCraft Pro».
- Для однократной покупки явно выбирается purchase option `pro-lifetime` и передаётся её offerToken.
- Добавлены понятные сообщения о состоянии Billing прямо на экране Pro.
- Если Google Play не возвращает товар или способ покупки, приложение показывает причину вместо визуального «мигания» кнопки.
- versionCode повышен до 114, versionName: `1.0.0-rc8-billing-fix`.
- Рабочий GitHub Actions workflow с проверкой upload key сохранён без отката.

Google Play product ID: `stitchcraft_pro_lifetime`
Google Play purchase option ID: `pro-lifetime`
