# Bandeja · marcador de pádel para Wear OS

App para Galaxy Watch (Wear OS 3 o superior) que lleva el tanteo de un partido de pádel.

## Qué hace
- Dos botones grandes (azul y rojo): tocas el de la pareja que gana el punto.
- Puntos 0 / 15 / 30 / 40 / AD, juegos, sets, tie-break a 7 y super tie-break a 10 opcional.
- Modo del 40-40 configurable: **punto de oro**, **ventajas** (iguales y ventaja) o **star point**
  (dos ventajas normales; si se llega al tercer 40-40, punto decisivo).
- Partido al mejor de 3 sets o a 1 set.
- Indicador de saque (pelota amarilla) que rota solo, también en el tie-break.
- Hora, pulso en directo y cronómetro del partido (arranca con el primer punto).
- Botón deshacer ilimitado y vibración distinta para punto, juego, set y partido.
- El partido se guarda: si la app se cierra, al abrirla sigue donde estaba.
- Deslizar para salir está desactivado para no cerrar el marcador sin querer.

## Instalar en el reloj
1. Descarga `Bandeja.apk` (pestaña **Releases** del repositorio).
2. En el reloj: Ajustes → Información del reloj → Información de software →
   toca **Versión de software** 5 veces para activar las Opciones de desarrollador.
3. Ajustes → Opciones de desarrollador → activa **Depuración ADB** y **Depuración inalámbrica**.
   Toca *Depuración inalámbrica* → **Vincular nuevo dispositivo**: verás una IP:puerto y un código.
4. En el PC (Windows) descarga las *SDK Platform Tools* de Android, descomprímelas y abre una terminal ahí:
   ```
   adb pair IP:PUERTO_DE_VINCULACION   (escribe el código)
   adb connect IP:PUERTO               (el que aparece en la pantalla de Depuración inalámbrica)
   adb install Bandeja.apk
   ```
   El PC y el reloj deben estar en la misma Wi-Fi.

Alternativa sin PC: apps de Android como *Bugjaeger* o *Easy Fire Tools* instalan el APK
desde el móvil al reloj usando la misma depuración inalámbrica.

## Compilar
GitHub Actions compila el APK en cada push a `main` (`.github/workflows/build.yml`).
En local: abrir la carpeta con Android Studio y ejecutar en el reloj o en un emulador Wear OS.
