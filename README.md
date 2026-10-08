# Planner

Планировщик задач для Android (MVP): задачи со сроком, локальное хранение, уведомления.
Подробнее об идее, стеке и плане — в [docs/IDEA_AND_PLAN.md](docs/IDEA_AND_PLAN.md).

**Стек:** Kotlin, Jetpack Compose (Material 3), Room, AlarmManager + BroadcastReceiver.

## Сборка
Нужны JDK 17 и Android SDK (compileSdk 35).

```
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit-тесты
```
