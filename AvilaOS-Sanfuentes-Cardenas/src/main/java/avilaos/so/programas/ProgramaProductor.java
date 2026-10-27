package avilaos.so.programas;

import avilaos.enums.RolSemaforo;

/** Productor del algoritmo de Stallings con buffer acotado. */
public class ProgramaProductor extends ProgramaBuffer {

    /** Crea un productor que tarda K ciclos en producir cada uno de sus N elementos. */
    public ProgramaProductor(String buffer, int ciclosPorElemento, int elementos) {
        super(buffer, ciclosPorElemento, elementos);
    }

    /** Produce la instrucción que toca en k posición. Previene interbloqueo. */
    @Override
    protected Instruccion instruccionEnElemento(int posicion) {
        int k = getCiclosPorElemento();
        if (posicion < k) {
            return Instruccion.CALCULO;
        }
        return switch (posicion - k) {
            case 0 -> Instruccion.semWait(RolSemaforo.VACIO);
            case 1 -> Instruccion.semWait(RolSemaforo.MUTEX);
            case 2 -> Instruccion.INSERTAR;
            case 3 -> Instruccion.semSignal(RolSemaforo.MUTEX);
            default -> Instruccion.semSignal(RolSemaforo.LLENO);
        };
    }

    /** Fichas que retiene el productor según cuántas instrucciones lleva de su elemento. */
    @Override
    protected RolSemaforo[] fichasEnPosicion(int ejecutadas) {
        return switch (ejecutadas - getCiclosPorElemento()) {
            case 1 -> new RolSemaforo[] {RolSemaforo.VACIO};
            case 2 -> new RolSemaforo[] {RolSemaforo.MUTEX, RolSemaforo.VACIO};
            case 3 -> new RolSemaforo[] {RolSemaforo.MUTEX, RolSemaforo.LLENO};
            case 4 -> new RolSemaforo[] {RolSemaforo.LLENO};
            default -> new RolSemaforo[0];
        };
    }

    @Override
    public String descripcion() {
        return "Productor en buffer " + getBuffer() + ", " + getElementos() + " elementos, uno cada "
                + getCiclosPorElemento() + " ciclos";
    }
}
