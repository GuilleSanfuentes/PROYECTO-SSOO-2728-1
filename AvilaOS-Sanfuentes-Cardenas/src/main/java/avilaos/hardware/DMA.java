package avilaos.hardware;

import avilaos.enums.TipoInterrupcion;

/** Controlador de E/S por acceso directo a memoria: atiende la E/S sin ocupar la CPU. */
public class DMA extends ControladorEspera {

    /** Crea el DMA conectado a la línea de interrupciones de su computador. */
    public DMA(String nombre, LineaInterrupciones linea) {
        super(nombre, TipoInterrupcion.FIN_ES, linea);
    }
}
