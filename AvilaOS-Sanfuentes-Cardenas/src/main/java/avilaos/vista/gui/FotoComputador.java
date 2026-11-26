package avilaos.vista.gui;

import avilaos.cluster.Computador;
import avilaos.enums.ModoCPU;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.so.ColasKernel;
import avilaos.so.Kernel;
import avilaos.so.PCB;
import avilaos.so.programas.Instruccion;

/** Foto inmutable de un computador: CPU, RAM, política y sus siete colas, lista para dibujarse en el hilo de Swing. */
public record FotoComputador(
        String nombre,
        FilaProceso enCPU,
        ModoCPU modo,
        boolean ociosa,
        String ultimaAccion,
        int contadorCPU,
        int marCPU,
        int ramUsada,
        int ramCapacidad,
        PoliticaTipo politica,
        int quantum,
        FilaProceso[] nuevos,
        FilaProceso[] listos,
        FilaProceso[] bloqueados,
        FilaProceso[] terminados,
        String instruccionPC,
        FilaProceso[] listosSuspendidos,
        FilaProceso[] bloqueadosSuspendidos) {

    /** Toma la foto del computador: las colas se copian de una vez bajo el semáforo del Kernel. */
    public static FotoComputador de(Computador computador) {
        Kernel kernel = computador.getKernel();
        CPU cpu = computador.getCpu();
        ColasKernel<FilaProceso> colas = kernel.fotografiarColas(FilaProceso::de, FotoComputador::instruccionEnPC);
        return new FotoComputador(computador.getNombre(), colas.enCPU(),
                cpu.getModo(), cpu.isOciosa(), cpu.getUltimaAccion(), cpu.getContador(), cpu.getMar(),
                computador.getRam().getUsada(), computador.getRam().getCapacidad(),
                kernel.getPolitica(), kernel.getQuantum(),
                arreglo(colas.nuevos()), arreglo(colas.listos()), arreglo(colas.bloqueados()), arreglo(colas.terminados()),
                colas.detalleCPU(), arreglo(colas.listosSuspendidos()), arreglo(colas.bloqueadosSuspendidos()));
    }

    /** Instrucción guardada en la dirección que indica el Contador (PC) del proceso en CPU: la próxima a ejecutar. */
    static String instruccionEnPC(PCB pcb) {
        if (pcb == null) {
            return "-";
        }
        Instruccion siguiente = pcb.instruccionActual();
        if (siguiente == null) {
            return "Contador " + pcb.getContador() + " → fin del programa";
        }
        return "Contador " + pcb.getContador() + " → " + siguiente
                + (siguiente.modo() == ModoCPU.SISTEMA_OPERATIVO ? "  (modo SO)" : "  (modo usuario)");
    }

    /** Busca el proceso por ID en la CPU y en las siete colas; null si no está. */
    public FilaProceso buscar(int id) {
        if (enCPU != null && enCPU.id() == id) {
            return enCPU;
        }
        for (FilaProceso[] cola : new FilaProceso[][]{nuevos, listos, bloqueados, listosSuspendidos, bloqueadosSuspendidos, terminados}) {
            for (FilaProceso fila : cola) {
                if (fila.id() == id) {
                    return fila;
                }
            }
        }
        return null;
    }

    private static FilaProceso[] arreglo(ListaEnlazada<FilaProceso> lista) {
        FilaProceso[] filas = new FilaProceso[lista.tamano()];
        Nodo<FilaProceso> nodo = lista.getCabeza();
        for (int i = 0; i < filas.length && nodo != null; i++) {
            filas[i] = nodo.getDato();
            nodo = nodo.getSiguiente();
        }
        return filas;
    }
}
