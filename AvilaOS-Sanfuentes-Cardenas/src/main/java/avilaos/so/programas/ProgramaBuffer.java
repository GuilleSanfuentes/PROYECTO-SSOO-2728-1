package avilaos.so.programas;

import avilaos.log.Debug;

/** Base de productor y consumidor: repite N veces un ciclo de K instrucciones de cálculo y 5 de SO. */
public abstract class ProgramaBuffer implements Programa {

    public static final int INSTRUCCIONES_SO_POR_ELEMENTO = 5;

    private final String buffer;
    private final int ciclosPorElemento;
    private final int elementos;

    /** Crea el programa para el buffer, con K ciclos de cálculo por elemento y N elementos en total. */
    protected ProgramaBuffer(String buffer, int ciclosPorElemento, int elementos) {
        this.buffer = buffer;
        this.ciclosPorElemento = ciclosPorElemento;
        this.elementos = elementos;
    }

    /** Instrucciones que ocupa un elemento completo: K de cálculo más 5 de SO. */
    public int largoPorElemento() {
        return ciclosPorElemento + INSTRUCCIONES_SO_POR_ELEMENTO;
    }

    @Override
    public int totalInstrucciones() {
        return elementos * largoPorElemento();
    }

    @Override
    public Instruccion instruccionEn(int pc) {
        if (pc < 0 || pc >= totalInstrucciones()) {
            Debug.error(getClass().getSimpleName(), "PC " + pc + " fuera del programa (0.." + (totalInstrucciones() - 1) + ")");
            return null;
        }
        return instruccionEnElemento(pc % largoPorElemento());
    }

    /** Instrucción en la posición indicada dentro del ciclo de un elemento. */
    protected abstract Instruccion instruccionEnElemento(int posicion);

    /** Identificador del buffer con el que trabaja. */
    public String getBuffer() {
        return buffer;
    }

    /** Ciclos de cálculo para producir o consumir un elemento. */
    public int getCiclosPorElemento() {
        return ciclosPorElemento;
    }

    /** Cantidad de elementos que debe procesar para terminar. */
    public int getElementos() {
        return elementos;
    }
}
