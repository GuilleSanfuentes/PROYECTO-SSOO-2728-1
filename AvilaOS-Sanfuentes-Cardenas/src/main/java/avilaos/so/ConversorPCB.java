package avilaos.so;

/** Convierte un PCB en una copia para quien observa (la interfaz); el Kernel la llama con su mutex tomado. */
public interface ConversorPCB<T> {

    /** Copia lo que se necesita del PCB; no debe guardar la referencia al PCB. */
    T convertir(PCB pcb);
}
