package avilaos.hardware;

import avilaos.enums.EstadoBloque;

/** Bloque del disco: su número es su posición; guarda su dueño y el número del siguiente bloque de la cadena. */
public record Bloque(int numero, EstadoBloque estado, String dueno, int siguiente) {

    /** Valor de siguiente para el último bloque de una cadena. */
    public static final int FIN = -1;

    /** Bloque libre de la zona indicada. */
    static Bloque libre(int numero, boolean swap) {
        return new Bloque(numero, swap ? EstadoBloque.SWAP_LIBRE : EstadoBloque.LIBRE, null, FIN);
    }

    /** Indica si el bloque tiene dueño. */
    public boolean isOcupado() {
        return estado.isOcupado();
    }

    /** Indica si es el último de su cadena. */
    public boolean esUltimo() {
        return siguiente == FIN;
    }
}
