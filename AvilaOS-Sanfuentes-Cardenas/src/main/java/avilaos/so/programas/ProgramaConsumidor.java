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

    @Override
    public String descripcion() {
        return "Consumidor en buffer " + getBuffer() + ", " + getElementos() + " elementos, uno cada "
                + getCiclosPorElemento() + " ciclos";
    }
}
