package com.rixradar.app

class FakeServerRadarRepository : RadarDataSource {

    override fun getDashboardState(): DashboardUiState {
        return DashboardUiState(
            cityText = "Рига, Латвия",
            updatedText = "Обновлено: серверный режим",
            demandValueText = "91/100",
            demandStatusText = "Сервер: очень высокий спрос",
            flight1Text = "12:05   LH892   Frankfurt   Серверный приоритет",
            flight2Text = "12:20   BT224   Helsinki    Серверный поток",
            flight3Text = "12:45   FR7711  London STN  Серверная оценка: высокий",
            event1Text = "Рига: сервер видит высокий городской интерес",
            event2Text = "Юрмала: сервер поднимает приоритет",
            event3Text = "Центр: высокий шанс хороших маршрутов",
            aiText = "Серверный режим: сейчас один из наиболее перспективных интервалов. Основной интерес — аэропорт, центр, гостиницы и Юрмала."
        )
    }

    override fun getFlightsState(): FlightsUiState {
        return FlightsUiState(
            title = "Ближайшие прилёты RIX",
            subtitle = "Fake server mode. Позже здесь будут реальные данные сервера.",
            blockTitle = "Серверные рейсы",
            route1 = "Франкфурт",
            meta1 = "LH892 • Lufthansa",
            time1 = "По расписанию: 12:05",
            status1 = "Статус: высокий приоритет",
            route2 = "Хельсинки",
            meta2 = "BT224 • airBaltic",
            time2 = "По расписанию: 12:20",
            status2 = "Статус: средне-высокий приоритет",
            route3 = "Лондон Stansted",
            meta3 = "FR7711 • Ryanair",
            time3 = "По расписанию: 12:45",
            status3 = "Статус: сильный поток",
            route4 = "Осло",
            meta4 = "DY1092 • Norwegian",
            time4 = "По расписанию: 13:10",
            status4 = "Статус: ожидается вовремя",
            hint = "Это тестовый серверный источник. Потом сюда придут реальные рейсы с твоего backend."
        )
    }

    override fun getEventsState(): EventsUiState {
        return EventsUiState(
            title = "Значимые события",
            subtitle = "Fake server mode. Позже здесь будут реальные события из интернета.",
            blockTitle = "Серверные события",
            city1 = "Рига",
            title1 = "Сервер обнаружил событие повышенной значимости",
            meta1 = "Сегодня • высокий приоритет",
            city2 = "Юрмала",
            title2 = "Серверный сигнал о повышенном туристическом спросе",
            meta2 = "Сегодня • высокий приоритет",
            city3 = "Сигулда",
            title3 = "Серверный средний интерес к дальним поездкам",
            meta3 = "Ближайшие дни • средний приоритет",
            city4 = "Огре",
            title4 = "Серверный фоновый региональный интерес",
            meta4 = "Ближайшие дни • средний приоритет",
            hint = "Это тестовый серверный источник. Позже сюда пойдут реальные мероприятия."
        )
    }

    override fun getForecastState(): ForecastUiState {
        return ForecastUiState(
            title = "Прогноз спроса",
            subtitle = "Fake server mode. Позже сюда подключим реальный расчёт.",
            nowTitle = "Сейчас",
            nowValue = "Очень высокий",
            nowMeta = "Сервер ожидает сильный момент",
            hour1Title = "Через 1 час",
            hour1Value = "Очень высокий",
            hour1Meta = "Сервер видит мощное окно",
            hour3Title = "Через 3 часа",
            hour3Value = "Высокий",
            hour3Meta = "После пика останется хороший спрос",
            bestWindowTitle = "Лучшее окно работы",
            bestWindowValue = "12:10–13:15",
            bestWindowMeta = "Серверная модель считает это главным интервалом",
            aiTitle = "Комментарий ИИ",
            aiText = "Серверный тестовый режим считает текущее окно одним из самых интересных. Основной акцент — аэропорт и поездки в центр.",
            hint = "Это тестовый серверный источник. Позже сюда придёт настоящая серверная аналитика."
        )
    }

    override fun getMapState(): MapUiState {
        return MapUiState(
            title = "Карта направлений",
            subtitle = "Fake server mode. Позже здесь будет реальная серверная карта.",
            schemeTitle = "Серверные зоны",
            zoneRixTitle = "Аэропорт RIX",
            zoneRixValue = "Очень высокий интерес",
            zoneRixMeta = "Сервер ставит аэропорт на первое место",
            zoneRigaTitle = "Рига / центр",
            zoneRigaValue = "Очень высокий интерес",
            zoneRigaMeta = "Сервер ждёт хороший городской поток",
            zoneJurmalaTitle = "Юрмала",
            zoneJurmalaValue = "Высокий интерес",
            zoneJurmalaMeta = "Сервер видит хорошие дорогие маршруты",
            zoneSiguldaTitle = "Сигулда",
            zoneSiguldaValue = "Средний интерес",
            zoneSiguldaMeta = "Сервер допускает дальние поездки",
            zoneOgreTitle = "Огре",
            zoneOgreValue = "Средний интерес",
            zoneOgreMeta = "Серверный фоновый интерес",
            hint = "Это тестовый серверный источник. Позже здесь будет живая оценка направлений."
        )
    }

    override fun getAiState(): AiUiState {
        return AiUiState(
            title = "Совет ИИ",
            subtitle = "Fake server mode. Позже здесь будет реальный совет от backend.",
            nowTitle = "Совет на сейчас",
            nowValue = "Сейчас стоит работать активно",
            nowMeta = "Серверный режим подтверждает сильный период",
            windowTitle = "Лучшее окно",
            windowValue = "12:10–13:15",
            windowMeta = "Сервер считает это лучшим окном",
            riskTitle = "Риск пустого ожидания",
            riskValue = "Низкий",
            riskMeta = "Сервер видит плотность потока",
            routesTitle = "Вероятные направления",
            routesValue = "Центр • гостиницы • Юрмала",
            routesMeta = "Серверный набор приоритетов",
            commentTitle = "Пояснение",
            commentText = "Это тестовый серверный совет. Он показывает, как приложение будет выглядеть после подключения к твоему backend.",
            hint = "Это тестовый серверный источник. Позже здесь появится настоящий AI-совет."
        )
    }
}