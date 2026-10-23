package avilaos.config;

import avilaos.log.Debug;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Semaphore;

/** Lectura y escritura del archivo JSON de configuración. */
public final class ArchivoConfiguracion {

    public static final String RUTA_POR_DEFECTO = "config/avilaos.json";
    public static final String CLAVE_DEBUGGER = "debugger";

    private static final String ORIGEN = "Config";
    private static final Semaphore acceso = new Semaphore(1);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ArchivoConfiguracion() {
    }

    /** Lee la clave debugger del archivo y activa o desactiva Debug. Si falla, lo deja activo. */
    public static void aplicarDebugger(String ruta) {
        JsonObject config;
        String fallo = null;
        try {
            config = leerSinMensajes(ruta);
        } catch (IOException | JsonParseException e) {
            config = valoresPorDefecto();
            fallo = "no se pudo leer " + ruta + ": " + e.getMessage();
        }
        Boolean valor = interpretarBooleano(config.get(CLAVE_DEBUGGER));
        if (valor == null && fallo == null) {
            fallo = "la clave '" + CLAVE_DEBUGGER + "' falta o no es TRUE/FALSE";
        }
        Debug.setActivo(valor == null || valor);
        if (fallo != null) {
            Debug.error(ORIGEN, fallo + "; debugger activado por defecto");
        } else {
            Debug.ok(ORIGEN, "debugger=" + (valor ? "TRUE" : "FALSE") + " leído de " + ruta);
        }
    }

    /** Lee el archivo completo; si no existe lo crea con valores por defecto. */
    public static JsonObject leer(String ruta) {
        try {
            JsonObject config = leerSinMensajes(ruta);
            Debug.ok(ORIGEN, "leído " + ruta);
            return config;
        } catch (IOException | JsonParseException e) {
            Debug.error(ORIGEN, "no se pudo leer " + ruta + ": " + e.getMessage() + "; se usan valores por defecto");
            return valoresPorDefecto();
        }
    }

    /** Guarda la configuración de forma atómica: escribe un temporal y lo renombra. */
    public static boolean guardar(String ruta, JsonObject config) {
        acceso.acquireUninterruptibly();
        try {
            escribirAtomico(Paths.get(ruta), config);
            Debug.ok(ORIGEN, "guardado " + ruta);
            return true;
        } catch (IOException e) {
            Debug.error(ORIGEN, "no se pudo guardar " + ruta + ": " + e.getMessage());
            return false;
        } finally {
            acceso.release();
        }
    }

    /** Configuración mínima usada cuando no hay archivo o es inválido. */
    public static JsonObject valoresPorDefecto() {
        JsonObject config = new JsonObject();
        config.addProperty(CLAVE_DEBUGGER, true);
        return config;
    }

    /** Acepta true/false o los textos "TRUE"/"FALSE" sin distinguir mayúsculas; si no, devuelve null. */
    private static Boolean interpretarBooleano(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitivo = elemento.getAsJsonPrimitive();
        if (primitivo.isBoolean()) {
            return primitivo.getAsBoolean();
        }
        if (primitivo.isString()) {
            String texto = primitivo.getAsString().trim();
            if (texto.equalsIgnoreCase("TRUE")) {
                return true;
            }
            if (texto.equalsIgnoreCase("FALSE")) {
                return false;
            }
        }
        return null;
    }

    /** Lee el JSON en exclusión mutua, sin imprimir; crea el archivo por defecto si no existe. */
    private static JsonObject leerSinMensajes(String ruta) throws IOException {
        acceso.acquireUninterruptibly();
        try {
            Path archivo = Paths.get(ruta);
            if (!Files.exists(archivo)) {
                JsonObject porDefecto = valoresPorDefecto();
                escribirAtomico(archivo, porDefecto);
                return porDefecto;
            }
            try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                JsonElement raiz = JsonParser.parseReader(lector);
                if (!raiz.isJsonObject()) {
                    throw new JsonParseException("la raíz del archivo no es un objeto JSON");
                }
                return raiz.getAsJsonObject();
            }
        } finally {
            acceso.release();
        }
    }

    /** Escribe en archivo.tmp y lo mueve sobre el definitivo para no dejarlo a medias. */
    private static void escribirAtomico(Path archivo, JsonObject config) throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        try (Writer escritor = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
            GSON.toJson(config, escritor);
        }
        try {
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
