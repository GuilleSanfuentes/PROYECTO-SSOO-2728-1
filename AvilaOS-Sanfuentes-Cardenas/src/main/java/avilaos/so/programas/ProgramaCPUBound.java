package avilaos.so.programas;

import avilaos.log.Debug;

/** Programa que solo realiza cálculo en modo usuario. */
public class ProgramaCPUBound implements Programa {

    private final int instrucciones;

    /** Crea un programa de la cantidad de instrucciones indicada. */
    public ProgramaCPUBound(int instrucciones) {
        this.instrucciones = instrucciones;
    }

    /** Devuelve siempre una instrucción de cálculo. */
    @Override
    public Instruccion instruccionEn(int pc) {
        if (pc < 0 || pc >= instrucciones) {
            Debug.error("CPUBound", "PC " + pc + " fuera del programa (0.." + (instrucciones - 1) + ")");
            return null;
        }
        return Instruccion.CALCULO;
    }

    @Override
    public int totalInstrucciones() {
        return instrucciones;
    }

    @Override
    public String descripcion() {
        return "CPU bound, " + instrucciones + " instrucciones";
    }
}
