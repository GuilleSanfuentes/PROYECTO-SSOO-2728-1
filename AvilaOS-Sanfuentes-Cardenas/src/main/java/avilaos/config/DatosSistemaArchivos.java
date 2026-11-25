package avilaos.config;

import avilaos.estructuras.ListaEnlazada;
import avilaos.so.archivos.DatosEntrada;

/** Lo leído del JSON del sistema de archivos: disco, entradas válidas y la lista de problemas encontrados. */
public record DatosSistemaArchivos(boolean cargable, int totalBloques, int cabezalInicial,
        ListaEnlazada<DatosEntrada> entradas, ListaEnlazada<String> problemas) {
}
