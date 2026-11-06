package space.tihsmile.trivogasim.core

/** User-provided parody audio/image pack. Not an actual civil-defence alert. */
data class RoflEntry(val id: String, val title: String, val body: String,
                     val audioFile: String, val imageFile: String)

object RoflCatalog {
    val entries = listOf(
        RoflEntry("horses", "DON'T PUSH THE HORSES!", "Увага! Коні вже запушені. Усик просив цього не робити, але тепер пізно.", "dont-push-the-horses.mp3", "dontpushthehorses.png"),
        RoflEntry("potuzhna", "ПОТУЖНА ТРИВОГА!", "Рівень потужності досяг потужного максимуму. Зєлєбоба взлетів на своєму потужнольоті.", "potuzhnolit.mp3", "potuzhnolit.png"),
        RoflEntry("poland", "Я В ПОЛЬЩІ!", "Увага! Абонент уже в Польщі. Повторюємо: ВІН У ПОЛЬЩІ. Про це вже знає весь інтернет.", "yaupolshi.mp3", "yaupolshi.png"),
        RoflEntry("evening", "ДОБРОГО ВЕЧОРА, МИ З УКРАЇНИ!", "На весь район увімкнули знайомий трек. Сусіди вже підспівують, навіть ті, хто не планував.", "probass-hardi-dobrogo-vechora.mp3", "dobrogovechora.png"),
        RoflEntry("sra", "ІДІТЬ В СРАКУ!", "Диспетчер отримав настільки геніальне запитання, що офіційна відповідь умістилася у три слова.", "idit-u-sraku.mp3", "idit-u-sraku.png"),
        RoflEntry("pig", "ПАМ'ЯТНИК СВИНІ!", "Увага! У центрі населеного пункту урочисто відкрили пам'ятник свині. Делегація вже прибула на поклоніння.", "pamyatnik-svini.mp3", "pamyatniksvini.png"),
        RoflEntry("jet2", "NOTHING BEATS A JET2 HOLIDAY!", "Ви замовили відпустку мрії. Автобус зламався, готель затопило, але реклама все ще переконує, що все чудово.", "jet2-holiday.mp3", "jet2holidays.png"),
        RoflEntry("chill", "I'M JUST A CHILL GUY", "Навколо паніка, повідомлення сиплються одне за одним. Один чувак просто стоїть із руками в кишенях і чілить.", "chill-guy.mp3", "chillguy.png"),
        RoflEntry("symphony", "I JUST WANNA BE PART OF YOUR SYMPHONY!", "У небі з'явився рожевий дельфін. На фоні веселка, а ситуація чомусь стає дедалі драматичнішою.", "i-just-wanna-be-part-of-your-symphony-yeah.mp3", "ijustbepartofyoursymphony.png"),
        RoflEntry("grr", "DAME UN GRRR!", "Невідомий об'єкт вимагає GRRR. Диспетчер перепитав: «¿Un qué?» Переговори зайшли в глухий кут.", "dame-un-grr.mp3", "dame-un-grr.png")
    )
    fun byId(id: String): RoflEntry? = entries.firstOrNull { it.id == id }
}
