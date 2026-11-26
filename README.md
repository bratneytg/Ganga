# Ganga

Apunta lo que quieres comprar y Ganga busca el mejor precio **4 veces al día** en cientos de tiendas de varios países. Compara el **precio total** (producto + envío hasta tu casa + importación si la tienda está fuera de la UE), guarda el historial y te avisa cuando algo baja de tu precio objetivo o toca mínimo histórico.

Hay dos versiones con la misma lógica:

- **Android** (carpeta `android/`): la app del móvil. Busca en el propio teléfono y te avisa con una notificación. Es la versión principal.
- **PC** (Node.js, resto del repositorio): la misma idea en una página local para el ordenador.

Sin cuentas ni servidores: tus datos se quedan en tu dispositivo.

## App de Android

### Instalarla desde el móvil (sin Android Studio)

1. En GitHub abre la pestaña **Actions** de este repositorio y entra en la última ejecución verde de **Android APK** (si no hay ninguna, pulsa **Run workflow**).
2. Abajo, en **Artifacts**, descarga **Ganga-debug-apk** (un .zip con `app-debug.apk` dentro).
3. Descomprime, abre el APK en el móvil y acepta «instalar apps desconocidas» para el navegador o el gestor de archivos.
4. Al abrir Ganga acepta el permiso de notificaciones.

### O compilarla en Android Studio

Abre la carpeta `android/`, deja que Gradle sincronice y ejecútala en tu móvil. Necesita JDK 17 (el que trae Android Studio sirve).

### Primeros pasos

1. Pulsa **Ajustes** y pega tu clave de Serper (ver «Fuentes de precios»). Sin ninguna fuente, la app te lo avisa; con el **modo demo** puedes probar la interfaz con ofertas inventadas.
2. Elige tu país y código postal, y los países donde buscar.
3. Escribe el artículo y, si quieres, tu precio objetivo, y pulsa **Vigilar**.
4. **Compartir desde el navegador**: en cualquier web o app de tienda, Compartir → Ganga abre el editor con el enlace ya puesto.

### Lo que conviene saber en Android

- Las búsquedas programadas (00:00, 06:00, 12:00 y 18:00 por defecto) las gestiona Android. Para ahorrar batería puede retrasarlas unos minutos o más, sobre todo con el ahorro de batería activado. Si quieres más puntualidad, en Ajustes del sistema → Apps → Ganga → Batería, elige «Sin restricciones».
- Si el móvil estaba apagado o sin datos, la búsqueda se hace en cuanto vuelve la conexión. Además, al abrir la app busca si la última tiene más de 6 horas.
- Gasto de Serper: 1 consulta por artículo, país y búsqueda. Con 5 países y 4 búsquedas al día son unas 600 consultas al mes **por artículo**. Con 2.500 consultas gratuitas, conviene vigilar pocos países o pocos artículos; Ajustes muestra la estimación.
- Las claves se guardan en el almacenamiento privado de la app, no se suben a ningún sitio.

## Versión de PC

Funciona en tu ordenador (Node.js). Tus datos se quedan en `data/` y no se suben a GitHub.

### Cómo arrancarla (Windows)

1. Instala [Node.js LTS](https://nodejs.org) (solo la primera vez).
2. Haz doble clic en `iniciar.bat`, o desde PowerShell:
   ```powershell
   npm install
   npm start
   ```
3. Abre <http://localhost:3000>, pulsa **Ajustes** y configura al menos una fuente de precios (ver abajo).

Déjala abierta: busca sola a las 00:00, 06:00, 12:00 y 18:00 (se cambia en Ajustes). Si el ordenador estaba apagado o dormido, al volver hace la búsqueda que se perdió.

### Fuentes de precios

| Fuente | Necesita | Qué aporta |
|---|---|---|
| Google Shopping (Serper) | clave gratuita en [serper.dev](https://serper.dev) (2.500 consultas de regalo) | Cientos de tiendas por país. Es la fuente principal. |
| eBay | claves gratuitas en [developer.ebay.com](https://developer.ebay.com) | Precio, país del vendedor y **envío real** hasta tu casa. |
| Páginas concretas | nada | Pegas la URL de una ficha de tienda y Ganga lee su precio (datos estructurados de la página). No funciona en tiendas que bloquean robots, como Amazon. |
| Demo | nada | Ofertas inventadas para probar la interfaz. |

Cada búsqueda en Serper gasta 1 consulta por país. Ajustes te muestra el gasto mensual estimado según tus artículos, países y horas.

Las claves también pueden ir en variables de entorno: `SERPER_API_KEY`, `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `NTFY_TOPIC`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`.

### Cómo calcula el precio total

- Si la tienda indica el envío hacia tu país (eBay, o la misma tienda local), se usa tal cual.
- Si no, se estima con tus tarifas de Ajustes: mismo país, otro país de la UE o fuera de la UE.
- Tiendas fuera de la UE (Reino Unido, Suiza, EE. UU.): se suma un porcentaje de IVA/aduana (19 % por defecto).
- Todo se convierte a euros con los tipos del BCE (se actualizan solos).
- Las estimaciones se marcan con `~` en la tabla de ofertas.

Filtros para no ver basura: descarta accesorios (fundas, cables, repuestos), exige que los números del modelo coincidan (un WH-1000XM4 no sirve si buscas el XM5), permite palabras obligatorias y excluidas, y aparta las ofertas con un precio sospechosamente por debajo de la mediana.

### Avisos al móvil desde el PC

- **ntfy**: instala la app [ntfy](https://ntfy.sh), suscríbete a un tema con nombre largo y difícil de adivinar y ponlo en Ajustes.
- **Telegram**: crea un bot con @BotFather y pon su token y tu ID de chat.

Avisa cuando el mejor precio total baja de tu objetivo o llega a un mínimo histórico, y no repite el aviso al mismo precio.

### Limitaciones que conviene conocer

- Google Shopping no dice si una tienda de otro país **envía a tu país**. Esas ofertas llevan la etiqueta «Tienda de …»: comprueba el envío antes de pagar.
- El «envío gratis» que muestra Google solo se da por bueno si la tienda es de tu país; en el resto se usa la estimación.
- Los enlaces de Google Shopping abren la ficha del producto en Google, donde eliges la tienda.
- El ordenador debe estar encendido con Ganga abierta. Para que arranque con Windows, pon un acceso directo a `iniciar.bat` en `shell:startup`.
- La app solo escucha en `localhost` y rechaza peticiones de otras webs; no la expongas a internet.

### Desarrollo

```powershell
npm test      # pruebas del motor (precios, filtros, envío, avisos, horario)
```

- `src/providers/`: una fuente de precios por archivo. Para añadir otra, exporta `{ id, name, isConfigured(settings), search(item, ctx) }` y regístrala en `index.js`; `search` devuelve `{ offers, warnings }`.
- `src/engine.js`: convierte ofertas en precios totales comparables, guarda historial y decide los avisos.
- `public/`: la interfaz (HTML, CSS y JS sin dependencias).
