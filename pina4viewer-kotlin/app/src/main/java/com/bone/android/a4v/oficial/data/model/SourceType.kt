package com.bone.android.a4v.oficial.data.model

enum class SourceType(val title: String, val shortCode: String, val url: String) {
    SERVER_IN(
        title = "ArenaVision (IN)",
        shortCode = "IN(D)",
        url = "http://www.arena4viewer.in/misguia2.php"
    ),
    SERVER_PL(
        title = "ArenaVision (PL)",
        shortCode = "PL(D)",
        url = "https://www.arena4viewer.pl/misguia2.php"
    ),
    OFF_MODE(
        title = "PIÑAVISION (Agenda Propia)",
        shortCode = "PIÑAVISION",
        url = "https://raw.githubusercontent.com/ArribaJesucristo/pina4viewer/main/agenda.json"
    ),
    SERVER_CO_IN(
        title = "ArenaVision (CO.IN)",
        shortCode = "CO.IN(D)",
        url = "https://www.arena4viewer.co.in/misguia2.php"
    ),
    SERVER_COOL(
        title = "ArenaVision (COOL)",
        shortCode = "COOL(D)",
        url = "https://www.arena4viewer.cool/misguia2.php"
    ),
    SERVER_INFO(
        title = "ArenaVision (INFO)",
        shortCode = "INFO(D)",
        url = "https://www.arena4viewer.info/misguia2.php"
    ),
    SERVER_TOP(
        title = "ArenaVision (TOP)",
        shortCode = "TOP(D)",
        url = "https://www.arena4viewer.top/misguia2.php"
    ),
    SERVER_LV(
        title = "ArenaVision (LV)",
        shortCode = "LV(D)",
        url = "https://www.arena4viewer.lv/misguia2.php"
    ),
    SEARCH(
        title = "CAIDO 2 (Peticiones)",
        shortCode = "CAIDO 2",
        url = "https://raw.githubusercontent.com/Icastresana/lista1/main/peticiones"
    ),
    CAIDO(
        title = "CAIDO 1 (MarkelLinks)",
        shortCode = "CAIDO 1",
        url = "https://www.markellinks.app/assets/links.json"
    ),
    PETICIONES(
        title = "Peticiones Directas",
        shortCode = "PETI",
        url = "https://raw.githubusercontent.com/Icastresana/lista1/main/peticiones"
    )
}
