package avilaos.so.programas;

import avilaos.log.Debug;

/** Programa de un proceso de E/S de archivos: instrucciones de cálculo y, al final, la solicitud al disco. */
public class ProgramaArchivo implements Programa {

    private final int instrucciones;
    private final String pedido;

    /** Crea el programa; pedido describe la operación para el log. */
    public ProgramaArchivo(int instrucciones, String pedido) {
        this.instrucciones = instrucciones;
        this.pedido = pedido;
    }

    /** La última instrucción es la solicitud al disco; las anteriores son cálculo. */
    @Override
    public Instruccion instruccionEn(int contador) {
        if (contador < 0 || contador >= instrucciones) {
            Debug.error("ProgramaArchivo", "Contador " + contador + " fuera del programa (0.." + (instrucciones - 1) + ")");
            return null;
        }
        return contador == instrucciones - 1 ? Instruccion.SOLICITAR_DISCO : Instruccion.CALCULO;
    }

    @Override
    public int totalInstrucciones() {
        return instrucciones;
    }

    @Override
    public String descripcion() {
        return "E/S de archivos (" + pedido + "), " + instrucciones + " instrucciones";
    }
}
