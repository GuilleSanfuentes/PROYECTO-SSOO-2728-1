package avilaos.so.programas;

import avilaos.enums.RolSemaforo;

/** Consumidor del algoritmo de Stallings con buffer acotado. */
public class ProgramaConsumidor extends ProgramaBuffer {

    /** Crea un consumidor que tarda K ciclos en consumir cada uno de sus N elementos. */
    public ProgramaConsumidor(String buffer, int ciclosPorElemento, int elementos) {
        super(buffer, ciclosPorElemento, elementos);
    }

    /** Inverso a Productor, extrae la instrucción según la misma escala. */
    @Override
    protected Instruccion instruccionEnElemento(int posicion) {
        return switch (posicion) {
            case 0 -> Instruccion.semWait(RolSemaforo.LLENO);
            case 1 -> Instruccion.semWait(RolSemaforo.MUTEX);
            case 2 -> Instruccion.EXTRAER;
            case 3 -> Instruccion.semSignal(RolSemaforo.MUTEX);
            case 4 -> Instruccion.semSignal(RolSemaforo.VACIO);
            default -> Instruccion.CALCULO;
        };
    }

    /** Fichas que retiene el consumidor según cuántas instrucciones lleva de su elemento. */
    @Override
    protected RolSemaforo[] fichasEnPosicion(int ejecutadas) {
        return switch (ejecutadas) {
            case 1 -> new RolSemaforo[] {RolSemaforo.LLENO};
            case 2 -> new RolSemaforo[] {RolSemaforo.MUTEX, RolSemaforo.LLENO};
            case 3 -> new RolSemaforo[] {RolSemaforo.MUTEX, RolSemaforo.VACIO};
            case 4 -> new RolSemaforo[] {RolSemaforo.VACIO};
            default -> new RolSemaforo[0];
        };
    }

    @Override
    public String descripcion() {
        return "Consumidor en buffer " + getBuffer() + ", " + getElementos() + " elementos, uno cada "
                + getCiclosPorElemento() + " ciclos";
    }
}
