package avilaos.so.programas;

import avilaos.log.Debug;

/** Programa que cada cierta cantidad de instrucciones solicita una operación de E/S. */
public class ProgramaIOBound implements Programa {

    private final int instrucciones;
    private final int cadaCiclos;
    private final int duracionES;

    /** Crea un programa que pide E/S cada cadaCiclos instrucciones y cada E/S dura duracionES ciclos. */
    public ProgramaIOBound(int instrucciones, int cadaCiclos, int duracionES) {
        this.instrucciones = instrucciones;
        this.cadaCiclos = cadaCiclos;
        this.duracionES = duracionES;
    }

    /** La última instrucción de cada tramo de cadaCiclos es una solicitud de E/S; el resto es cálculo. */
    @Override
    public Instruccion instruccionEn(int contador) {
        if (contador < 0 || contador >= instrucciones) {
            Debug.error("IOBound", "Contador " + contador + " fuera del programa (0.." + (instrucciones - 1) + ")");
            return null;
        }
        return (contador + 1) % cadaCiclos == 0 ? Instruccion.SOLICITAR_ES : Instruccion.CALCULO;
    }

    @Override
    public int totalInstrucciones() {
        return instrucciones;
    }

    /** Cada cuántas instrucciones se solicita E/S. */
    public int getCadaCiclos() {
        return cadaCiclos;
    }

    /** Ciclos que dura cada operación de E/S. */
    public int getDuracionES() {
        return duracionES;
    }

    @Override
    public String descripcion() {
        return "I/O bound, " + instrucciones + " instrucciones, E/S cada " + cadaCiclos
                + " instrucciones con duración " + duracionES + " ciclos";
    }
}
