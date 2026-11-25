package avilaos.config;

import avilaos.estructuras.ListaEnlazada;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;

/** Nombres de las claves de un objeto JSON en una lista propia (sin colecciones de Java). */
final class ClavesJson {

    private ClavesJson() {
    }

    /** Claves del objeto en el orden en que aparecen; vacía si el objeto es nulo. */
    static ListaEnlazada<String> de(JsonObject objeto) {
        ListaEnlazada<String> claves = new ListaEnlazada<>();
        if (objeto == null) {
            return claves;
        }
        try (JsonReader lector = new JsonReader(new StringReader(objeto.toString()))) {
            lector.beginObject();
            while (lector.hasNext()) {
                claves.agregarFinal(lector.nextName());
                lector.skipValue();
            }
            lector.endObject();
        } catch (IOException e) {
            claves.vaciar();
        }
        return claves;
    }
}
