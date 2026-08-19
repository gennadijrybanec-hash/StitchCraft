# Сборка Android — чеклист

1. Открыть корень проекта в Android Studio.
2. Проверить Gradle sync.
3. Запустить Debug на физическом Android-устройстве.
4. Проверить:
   - выбор фото;
   - генерацию схемы;
   - сохранение/повторное открытие;
   - редактор;
   - прогресс;
   - PDF/PNG/CSV;
   - поворот экрана/возврат назад;
   - большие схемы.
5. Создать upload keystore.
6. Настроить release signing.
7. Собрать AAB:
   ./gradlew bundleRelease
8. Загрузить AAB во Internal testing Google Play Console.
9. Протестировать Billing на тестовом аккаунте.
10. Только после этого переводить релиз в production.
