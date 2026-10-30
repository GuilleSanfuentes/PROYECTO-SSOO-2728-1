package avilaos.so.programas;

import avilaos.enums.ModoCPU;
import avilaos.enums.RolSemaforo;
import avilaos.enums.TipoInstruccion;

/** Una instrucción del programa de un proceso; semaforo solo se usa en semWait y semSignal. */
public record Instruccion(TipoInstruccion tipo, RolSemaforo semaforo) {

    public static final Instruccion CALCULO = new Instruccion(TipoInstruccion.CALCULO, null);
    public static final Instruccion SOLICITAR_ES = new Instruccion(TipoInstruccion.SOLICITAR_ES, null);
    public static final Instruccion INSERTAR = new Instruccion(TipoInstruccion.INSERTAR, null);
    public static final Instruccion EXTRAER = new Instruccion(TipoInstruccion.EXTRAER, null);

    /** Crea un semWait sobre el semáforo indicado. */
    public static Instruccion semWait(RolSemaforo rol) {
        return new Instruccion(TipoInstruccion.SEM_WAIT, rol);
    }

    /** Crea un semSignal sobre el semáforo indicado. */
    public static Instruccion semSignal(RolSemaforo rol) {
        return new Instruccion(TipoInstruccion.SEM_SIGNAL, rol);
    }

    /** Modo de CPU en que se ejecuta. */
    public ModoCPU modo() {
        return tipo.getModo();
    }

    /** Texto del tipo semWait(vacío) o Cálculo. */
    @Override
    public String toString() {
        return semaforo == null ? tipo.getNombre() : tipo.getNombre() + "(" + semaforo.getNombre() + ")";
    }
}
