package space.tihsmile.trivogasim.core

/** User-provided parody audio/image pack. Not an actual civil-defence alert. */
data class RoflEntry(val id: String, val title: String, val body: String,
                     val audioFile: String, val imageFile: String, val custom: Boolean = false)

object RoflCatalog {
    val entries = listOf(
        RoflEntry("horses", "DON'T PUSH THE HORSES!", "Увага! Коні вже запушені. Усик просив цього не робити, але тепер пізно.", "dont-push-the-horses.mp3", "dontpushthehorses.png"),
        RoflEntry("potuzhna", "ПОТУЖНА ТРИВОГА!", "Рівень потужності досяг потужного максимуму. Зєлєбоба взлетів на своєму потужнольоті.", "potuzhnolit.mp3", "potuzhnolit.png"),
        RoflEntry("poland", "Я В ПОЛЬЩІ!", "Увага! Абонент уже в Польщі. Повторюємо: ВІН У ПОЛЬЩІ. Про це вже знає весь інтернет.", "yaupolshi.mp3", "yaupolshi.png"),
        RoflEntry("evening", "ДОБРОГО ВЕЧОРА, МИ З УКРАЇНИ!", "На весь район увімкнули знайомий трек. Сусіди вже підспівують, навіть ті, хто не планував.", "probass-hardi-dobrogo-vechora.mp3", "dobrogovechora.png"),
        RoflEntry("sra", "ІДІТЬ В СРАКУ!", "Диспетчер отримав настільки геніальне запитання, що офіційна відповідь умістилася у три слова.", "idit-u-sraku.mp3", "idit-u-sraku.png"),
        RoflEntry("pig", "ПАМ'ЯТНИК СВИНІ!", "Увага! У центрі населеного пункту урочисто відкрили пам'ятник свині. Делегація вже прибула на поклоніння.", "pamyatnik-svini.mp3", "pamyatniksvini.png")
        // Four unsuitable scenarios were removed in v0.5.
    )
    fun byId(id: String): RoflEntry? = entries.firstOrNull { it.id == id }
}
