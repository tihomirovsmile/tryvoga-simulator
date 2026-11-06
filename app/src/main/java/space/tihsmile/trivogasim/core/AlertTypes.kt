package space.tihsmile.trivogasim.core

enum class AlertKind(val label: String, val advice: String, val emoji: String, val color: Long,
                     val iconName: String, val hasAllClear: Boolean = true) {
    AIR("Повітряна тривога", "Пройдіть до найближчого укриття", "✈", 0xFFFF725C, "ic_alarm_air"),
    ARTILLERY("Артилерійський обстріл", "Терміново пройдіть до укриття", "✦", 0xFFFF9900, "ic_alarm_artillery"),
    FIGHTS("Вуличні бої", "Не виходьте з укриття", "⌖", 0xFF38CFC3, "ic_alarm_urban"),
    CHEMICAL("Хімічна загроза", "Не виходьте на вулицю", "☣", 0xFFD080FF, "ic_alarm_chemical"),
    RADIATION("Радіаційна небезпека", "Не виходьте на вулицю", "☢", 0xFFF8DF52, "ic_alarm_nuclear"),
    INCREASED("Підвищена небезпека!", "Перебувайте в укритті", "!", 0xFFFF725C, "ic_alarm_air", false),
    DRONE("Загроза БПЛА", "Перебувайте в укритті", "✈", 0xFFFF725C, "ic_alarm_air", false),
    MISSILE("Ракетна загроза", "Перебувайте в укритті", "➤", 0xFFFF725C, "ic_alarm_air", false),
    BALLISTIC("Балістична загроза", "Перебувайте в укритті", "➤", 0xFFFF725C, "ic_alarm_air", false),
    BOMB("Загроза КАБ", "Перебувайте в укритті", "◆", 0xFFFF725C, "ic_alarm_air", false),
    TECHNO("Техногенна небезпека", "Ознайомтеся з рекомендаціями", "⚠", 0xFFFF9900, "ic_alarm_custom", false),
    SPECIAL("Спеціальне повідомлення", "Ознайомтеся з повідомленням", "!", 0xFF7BB8FF, "ic_alarm_custom"),
    ROFL("Рофл тривога", "Жартівливе повідомлення", "♪", 0xFF9D86FF, "ic_alarm_custom");

    val isOneShot: Boolean get() = !hasAllClear
    companion object { fun from(value: String): AlertKind = entries.find { it.name == value } ?: SPECIAL }
}
