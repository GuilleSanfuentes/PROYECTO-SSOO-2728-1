package avilaos.pruebas;

import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoCPU;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoProceso;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Comparador;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import avilaos.log.LogEventos;

/** Pruebas de terminal de la etapa 1: estructuras, enums y log. */
public final class PruebasPaso1 {

    private static final String ORIGEN = "Pruebas";
    private static Resultados r;

    private record Elemento(String nombre, int prioridad) {
        @Override
        public String toString() {
            return nombre + "(" + prioridad + ")";
        }
    }

    private PruebasPaso1() {
    }

    /** Ejecuta todas las pruebas de la etapa 1 e imprime su resumen. */
    public static void ejecutar(LogEventos log) {
        r = new Resultados(1, "Etapa 1: estructuras, enums y log");
        probarLista();
        probarCola();
        probarOrdenEstable();
        probarErroresControlados();
        probarEnums();
        probarLog(log);
        r.cerrar();
    }

    /** Agregar, insertar, obtener y eliminar en la lista. */
    private static void probarLista() {
        r.grupo("Lista enlazada (agregar, insertar, obtener, eliminar, vaciar)");
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        r.verificar("lista nueva vacía", lista.estaVacia() && lista.tamano() == 0, lista.toString());
        lista.agregarFinal("B");
        lista.agregarFinal("C");
        lista.agregarInicio("A");
        lista.insertarEn(3, "D");
        lista.insertarEn(2, "X");
        r.verificar("agregar e insertar", lista.toString().equals("[A, B, X, C, D]"), lista.toString());
        r.verificar("obtener(2)", "X".equals(lista.obtener(2)), String.valueOf(lista.obtener(2)));
        r.verificar("eliminarEn(2)", "X".equals(lista.eliminarEn(2)) && lista.tamano() == 4, lista.toString());
        r.verificar("eliminar último", "D".equals(lista.eliminarEn(3)) && "C".equals(lista.ultimo()), lista.toString());
        lista.agregarFinal("E");
        r.verificar("último tras eliminar", "E".equals(lista.ultimo()) && lista.tamano() == 4, lista.toString());
        String objeto = lista.obtener(1);
        r.verificar("eliminar por referencia", lista.eliminar(objeto) && !lista.contiene(objeto), lista.toString());
        lista.vaciar();
        r.verificar("vaciar", lista.estaVacia() && lista.primero() == null && lista.ultimo() == null, lista.toString());
    }

    /** Orden FIFO de la cola y extracción de un elemento intermedio. */
    private static void probarCola() {
        r.grupo("Cola FIFO (encolar, desencolar, sacar intermedio)");
        Cola<String> cola = new Cola<>();
        cola.encolar("P1");
        cola.encolar("P2");
        cola.encolar("P3");
        r.verificar("frente de la cola", "P1".equals(cola.verFrente()), cola.toString());
        r.verificar("desencolar FIFO", "P1".equals(cola.desencolar()) && "P2".equals(cola.desencolar()), cola.toString());
        cola.encolar("P4");
        cola.encolar("P5");
        String intermedio = cola.obtener(1);
        r.verificar("sacar elemento intermedio", cola.eliminar(intermedio) && cola.toString().equals("[P3, P5]"), cola.toString());
    }

    /** El orden por prioridad conserva el orden de llegada en los empates. */
    private static void probarOrdenEstable() {
        r.grupo("Ordenamiento estable (empates conservan el orden de llegada)");
        Comparador<Elemento> porPrioridad = (a, b) -> Integer.compare(a.prioridad(), b.prioridad());
        Cola<Elemento> cola = new Cola<>();
        cola.encolar(new Elemento("A", 3));
        cola.encolar(new Elemento("B", 1));
        cola.encolar(new Elemento("C", 3));
        cola.encolar(new Elemento("D", 2));
        cola.encolar(new Elemento("E", 1));
        cola.ordenar(porPrioridad);
        r.verificar("ordenar estable", cola.toString().equals("[B(1), E(1), D(2), A(3), C(3)]"), cola.toString());
        cola.insertarOrdenado(new Elemento("F", 2), porPrioridad);
        cola.insertarOrdenado(new Elemento("G", 0), porPrioridad);
        cola.insertarOrdenado(new Elemento("H", 9), porPrioridad);
        r.verificar("insertarOrdenado estable", cola.toString().equals("[G(0), B(1), E(1), D(2), F(2), A(3), C(3), H(9)]"), cola.toString());
        r.verificar("tamaño tras ordenar", cola.tamano() == 8, String.valueOf(cola.tamano()));
    }

    /** Errores provocados a propósito: deben avisar con ERROR y ubicación, sin detener el programa. */
    private static void probarErroresControlados() {
        r.grupo("Errores controlados (no detienen el programa)");
        Debug.info(ORIGEN, "las 3 líneas ERROR siguientes son provocadas a propósito");
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        Cola<String> cola = new Cola<>();
        r.verificar("obtener fuera de rango", lista.obtener(5) == null, "no devolvió null");
        r.verificar("desencolar vacía", cola.desencolar() == null, "no devolvió null");
        r.verificar("insertar fuera de rango", !lista.insertarEn(-1, "Z") && lista.estaVacia(), lista.toString());
    }

    /** Los enums exponen sus nombres legibles y propiedades. */
    private static void probarEnums() {
        r.grupo("Enums (estados, tipos, políticas, modo CPU, motivos)");
        r.verificar("EstadoProceso", EstadoProceso.values().length == 5 && EstadoProceso.EJECUCION.getNombre().equals("Ejecución"), "");
        r.verificar("TipoProceso.usaBuffer", TipoProceso.PRODUCTOR.usaBuffer() && !TipoProceso.CPU_BOUND.usaBuffer(), "");
        r.verificar("PoliticaTipo.esApropiativa", !PoliticaTipo.FCFS.esApropiativa() && PoliticaTipo.PRIORIDADES.esApropiativa(), "");
        r.verificar("ModoCPU", ModoCPU.SISTEMA_OPERATIVO.getNombre().equals("Sistema operativo"), "");
        r.verificar("MotivoBloqueo", MotivoBloqueo.valueOf("SEMAFORO") == MotivoBloqueo.SEMAFORO, "");
    }

    /** El log guarda eventos y devuelve los últimos. */
    private static void probarLog(LogEventos log) {
        r.grupo("Log de eventos (registrar, últimos, copiar)");
        int antes = log.tamano();
        log.registrar(0, "Computador 1 selecciona el proceso P3-H1");
        log.registrar(0, "Proceso P5-H1 se bloquea en el semáforo 'lleno' del buffer B1");
        r.verificar("log registra", log.tamano() == antes + 2, String.valueOf(log.tamano()));
        r.verificar("log últimos", log.ultimos(1, "|").contains("P5-H1"), log.ultimos(1, "|"));
        r.verificar("log copiar", log.copiar().length == log.tamano(), "");
    }
}
