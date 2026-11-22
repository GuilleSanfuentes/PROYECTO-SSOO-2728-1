package avilaos.hardware;

/** Quien le dice al disco qué hacer en cada ciclo (el planificador de disco del SO); el disco no lo conoce. */
public interface ControladorDisco {

    /** Decide y ordena la única acción del disco en este ciclo: mover, transferir, saltar o quedar ocioso. */
    void ejecutarCicloDisco(DiscoSD disco, long ciclo);
}
