package com.bandeja.shared

// Generado desde la rutina web (rutina-gym.html). Si cambias la rutina, cámbiala aquí.
object Rutina {
    val dias: List<Dia> = listOf(
        Dia("martes", "Martes", "Empuje", "Pecho + hombro + tríceps", "Tras el partido del lunes, día sin pierna.", listOf(
            Ejercicio("Máquina de remo", "5 min", "—", "Ritmo suave: piernas, luego tronco, luego brazos.", "máquina de remo técnica", "warm"),
            Ejercicio("Press de banca con mancuernas", "4x8-10", "2 min", "Escápulas juntas, baja al centro del pecho con codos a 45°.", "press de banca con mancuernas técnica", "banca"),
            Ejercicio("Press militar con mancuernas", "3x8-10", "2 min", "Sentado con respaldo vertical; mancuernas a la altura de las orejas y sin arquear la lumbar.", "press militar con mancuernas sentado técnica", "militar"),
            Ejercicio("Press inclinado con mancuernas", "3x10-12", "90 s", "Banco a 30°; baja hasta la línea del pecho.", "press inclinado mancuernas técnica", "inclinado"),
            Ejercicio("Elevaciones laterales", "4x12-15", "60 s", "Sube hasta la altura del hombro con el codo un poco flexionado.", "elevaciones laterales técnica", "laterales"),
            Ejercicio("Extensión de tríceps sobre la cabeza con mancuerna", "3x12", "60 s", "Sentado con respaldo; codos apuntando al techo y quietos, baja la mancuerna detrás de la cabeza en 2-3 s.", "extensión de tríceps sobre la cabeza con mancuerna técnica", "tricepsOver"),
            Ejercicio("Extensión de tríceps en polea", "4x12", "60 s", "Codos pegados al cuerpo; solo se mueve el antebrazo.", "extensión de tríceps en polea técnica", "triceps"),
            Ejercicio("Plancha", "3x1 min", "1 min", "Glúteo y abdomen apretados, sin hundir la cadera.", "plancha abdominal técnica", "plancha"),
            Ejercicio("Estiramientos", "1.30 min", "—", "Pecho, hombro y tríceps, 30 s cada uno.", "estiramientos pecho hombro", "stretch"),
        )),
        Dia("jueves", "Jueves", "Pierna", "Fuerza y prevención", "Único día de pierna, a RIR 2-3 para llegar fresco a la bici del sábado: el viernes no hay pierna.", listOf(
            Ejercicio("Bici estática", "5 min", "—", "Ritmo suave y cadencia ágil; sube un poco la resistencia el último minuto.", "bici estática calentamiento", "bici"),
            Ejercicio("Hip thrust", "3x10-12", "90 s", "Mentón recogido; aprieta el glúteo 1 s arriba.", "hip thrust técnica", "hipThrust"),
            Ejercicio("Prensa 45° (rodilla hasta 90°)", "4x8-10", "2 min", "Pies a la anchura de cadera; no despegues la zona lumbar del respaldo.", "prensa 45 grados técnica", "prensa"),
            Ejercicio("Sentadilla búlgara (bajada lenta)", "3x8 por pierna", "90 s", "Peso en el pie de delante; baja en 2-3 s con la rodilla alineada.", "sentadilla búlgara técnica", "bulgara"),
            Ejercicio("Curl de piernas sentado", "3x10-12", "60 s", "Cadera pegada al asiento; baja en 3 s.", "curl femoral sentado técnica", "curlSent"),
            Ejercicio("Aductores en máquina (cara interna)", "3x15", "60 s", "Cierra controlado, sin rebotar.", "máquina aductores técnica", "aductor"),
            Ejercicio("Abductores en máquina (cara externa)", "3x15", "60 s", "Abre las piernas controlado y vuelve despacio; trabaja el glúteo medio.", "máquina abductores técnica", "abductor"),
            Ejercicio("Sóleo sentado en máquina", "3x15", "60 s", "Rodilla a 90°, pausa de 1 s arriba.", "sóleo sentado máquina técnica", "soleo"),
            Ejercicio("Estiramientos", "1.30 min", "—", "Isquios, cuádriceps, gemelo y aductores, 20-30 s cada uno.", "estiramientos piernas después de entrenar", "stretchLeg"),
        )),
        Dia("viernes", "Viernes", "Tirón", "Espalda + bíceps + core", "Sin pierna para llegar fresco a la bici del sábado.", listOf(
            Ejercicio("Máquina de remo", "5 min", "—", "Ritmo suave: piernas, luego tronco, luego brazos.", "máquina de remo técnica", "warm"),
            Ejercicio("Jalón al pecho", "4x8-10", "2 min", "Pecho alto; baja la barra a la parte alta del pecho llevando los codos abajo.", "jalón al pecho técnica", "jalon"),
            Ejercicio("Curl de bíceps con mancuernas", "3x10", "60 s", "Codos quietos, sin balancear el tronco.", "curl de bíceps con mancuernas técnica", "curlBarra"),
            Ejercicio("Remo aislado con mancuerna", "3x10 por brazo", "90 s", "Mano y rodilla en el banco, espalda recta; lleva el codo hacia la cadera sin girar el tronco.", "remo con mancuerna a una mano técnica", "remoMancuerna"),
            Ejercicio("Curl martillo con mancuernas", "3x12", "60 s", "Agarre neutro y codos quietos; refuerza el antebrazo para la pala.", "curl martillo técnica", "martillo"),
            Ejercicio("Remo polea baja (agarre estrecho)", "3x10-12", "90 s", "Pecho alto; lleva los codos atrás pegados al cuerpo.", "remo polea baja agarre estrecho técnica", "remoPolea"),
            Ejercicio("Rotadores externos en polea", "3x12-15", "60 s", "Poco peso y el codo pegado al cuerpo a 90°; gira el antebrazo hacia fuera sin mover el hombro.", "rotación externa de hombro en polea técnica", "rotExt"),
            Ejercicio("Plancha lateral", "3x1 min por lado", "1 min", "Codo bajo el hombro y cadera alta, en línea.", "plancha lateral técnica", "planchaLat"),
            Ejercicio("Estiramientos", "1.30 min", "—", "Dorsal, bíceps y antebrazo, 30 s cada uno.", "estiramientos espalda dorsal", "stretch"),
        )),
    )

    fun dia(id: String): Dia? = dias.firstOrNull { it.id == id }
}
