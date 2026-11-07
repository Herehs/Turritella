# Подключение UI

Контракты находятся в `src/app/src/main/java/ru/steno/presentation`. У экранов списка, настроек, записи и заметки единый вход `onIntent`, `StateFlow` для отображения и `effects` для одноразовых действий. Поля и события документированы в соответствующих *Contract.kt. В *PreviewData.kt есть примеры состояния. Примеры используются только при разработке интерфейса.

| ViewModel | Действия экрана | Как завершить действие |
|---|---|---|
| AppViewModel | Подписаться на тему в Activity | После isLoaded применить themeMode |
| MainViewModel | Поиск, сортировка, список, импорт или запись | На PickAudio открыть OpenDocument, вернуть AudioPicked(uri или null). OpenNote и OpenRecording меняют стек |
| RecordingViewModel | Старт, пауза, продолжение, сохранение | На RequestMicPermission запросить RECORD_AUDIO, вернуть MicPermissionResult. На OpenNote заменить маршрут записи |
| NoteViewModel | Отобразить расшифровку и статус, управлять плеером, экспортировать | Передать noteId параметром Koin. На PickExportDestination открыть CreateDocument с указанным MIME и вернуть ExportDestinationPicked, в том числе при отмене |
| SettingsViewModel | Тема, формат, пунктуация, число голосов, модели | Показать прогресс Whisper и обрабатывать ShowMessage |

Маршруты MainRoute, RecordingRoute и NoteRoute сериализуемы. В хосте задайте отдельный ViewModelStore для каждой записи стека. Нажатие на уведомление передаёт EXTRA_NOTE_ID или EXTRA_OPEN_RECORDING. Разрешение POST_NOTIFICATIONS запрашивает Activity. Подписку на эффекты запускайте один раз на экран.

Текст поля ввода принадлежит UI. Ввод передавайте немедленно, поиск и запись названия уже имеют задержку. Для подсветки реплики используйте activeUtteranceIndex. При возврате со страницы записи отправьте BackClicked и покажите подтверждение, если showExitDialog стал true. Названия участников по умолчанию определяйте по speakerNumber.

## Изменения

Первичная версия по разделу 7 задания. Данные состояния и маршруты готовы для подключения интерфейса. Сборка модуля проверяет контракты и логику отдельно от Composable.


### HexTeam — 8 ноября 2026

Экраны перенесены из debug в main и реализованы по макетам HexTeam. Добавлены `NoteItem.isFavorite` (по умолчанию false), `MainIntent.FavoriteClicked(id)` и `AppSettings.favoriteNoteIds`. Отметки избранного сохраняются в DataStore, схема Room и формат экспорта не меняются. Тема применяется во всех Activity. Иконка «+» открывает выбор записи или импорта; долгое нажатие карточки предлагает удаление, долгое нажатие реплики — имя участника.
