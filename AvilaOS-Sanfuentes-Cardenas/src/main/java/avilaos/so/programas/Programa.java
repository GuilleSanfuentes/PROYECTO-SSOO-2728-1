package avilaos.so.programas;

/** Comportamiento de un proceso: qué instrucción hay en cada dirección del Contador. */
public interface Programa {

    /** Devuelve la instrucción en la dirección contador, o null si está fuera del programa. */
    Instruccion instruccionEn(int contador);

    /** Cantidad total de instrucciones del programa. */
    int totalInstrucciones();

    /** Resumen de los parámetros del programa para el log. */
    String descripcion();
}
