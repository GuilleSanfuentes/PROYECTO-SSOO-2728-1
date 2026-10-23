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

/** Pruebas de terminal del paso 1: estructuras, enums y log. */
public final class PruebasPaso1 {

    private static final String ORIGEN = "Pruebas";
    private static int fallos;

    private record Elemento(String nombre, int prioridad) {
        @Override
        public String toString() {
            return nombre + "(" + prioridad + ")";
        }
    }

    private PruebasPaso1() {
    }

    /** Ejecuta todas las pruebas del paso 1 e informa el resultado. */
    public static void ejecutar(LogEventos log) {
        fallos = 0;
        Debug.info(ORIGEN, "===== PASO 1: estructuras, enums y log =====");
        probarLista();
        probarCola();
        probarOrdenEstable();
        probarErroresControlados();
        probarEnums();
        probarLog(log);
        if (fallos == 0) {
            Debug.ok(ORIGEN, "paso 1 completo, sin fallos");
        } else {
            Debug.error(ORIGEN, "paso 1 con " + fallos + " fallos");
        }
    }

    /** Agregar, insertar, obtener y eliminar en la lista. */
    private static void probarLista() {
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        verificar("lista nueva vacía", lista.estaVacia() && lista.tamano() == 0, lista.toString());
        lista.agregarFinal("B");
        lista.agregarFinal("C");
        lista.agregarInicio("A");
        lista.insertarEn(3, "D");
        lista.insertarEn(2, "X");
        verificar("agregar e insertar", lista.toString().equals("[A, B, X, C, D]"), lista.toString());
        verificar("obtener(2)", "X".equals(lista.obtener(2)), String.valueOf(lista.obtener(2)));
        verificar("eliminarEn(2)", "X".equals(lista.eliminarEn(2)) && lista.tamano() == 4, lista.toString());
        verificar("eliminar último", "D".equals(lista.eliminarEn(3)) && "C".equals(lista.ultimo()), lista.toString());
        lista.agregarFinal("E");
        verificar("último tras eliminar", "E".equals(lista.ultimo()) && lista.tamano() == 4, lista.toString());
        String objeto = lista.obtener(1);
        verificar("eliminar por referencia", lista.eliminar(objeto) && !lista.contiene(objeto), lista.toString());
        lista.vaciar();
        verificar("vaciar", lista.estaVacia() && lista.primero() == null && lista.ultimo() == null, lista.toString());
    }

    /** Orden FIFO de la cola y extracción de un elemento intermedio. */
    private static void probarCola() {
        Cola<String> cola = new Cola<>();
        cola.encolar("P1");
        cola.encolar("P2");
        cola.encolar("P3");
        verificar("frente de la cola", "P1".equals(cola.verFrente()), cola.toString());
        verificar("desencolar FIFO", "P1".equals(cola.desencolar()) && "P2".equals(cola.desencolar()), cola.toString());
        cola.encolar("P4");
        cola.encolar("P5");
        String intermedio = cola.obtener(1);
        verificar("sacar elemento intermedio", cola.eliminar(intermedio) && cola.toString().equals("[P3, P5]"), cola.toString());
    }

    /** El orden por prioridad conserva el orden de llegada en los empates. */
    private static void probarOrdenEstable() {
        Comparador<Elemento> porPrioridad = (a, b) -> Integer.compare(a.prioridad(), b.prioridad());
        Cola<Elemento> cola = new Cola<>();
        cola.encolar(new Elemento("A", 3));
        cola.encolar(new Elemento("B", 1));
        cola.encolar(new Elemento("C", 3));
        cola.encolar(new Elemento("D", 2));
        cola.encolar(new Elemento("E", 1));
        cola.ordenar(porPrioridad);
        verificar("ordenar estable", cola.toString().equals("[B(1), E(1), D(2), A(3), C(3)]"), cola.toString());
        cola.insertarOrdenado(new Elemento("F", 2), porPrioridad);
        cola.insertarOrdenado(new Elemento("G", 0), porPrioridad);
        cola.insertarOrdenado(new Elemento("H", 9), porPrioridad);
        verificar("insertarOrdenado estable", cola.toString().equals("[G(0), B(1), E(1), D(2), F(2), A(3), C(3), H(9)]"), cola.toString());
        verificar("tamaño tras ordenar", cola.tamano() == 8, String.valueOf(cola.tamano()));
    }

    /** Operaciones inválidas devuelven null o false sin detener el programa. */
    private static void probarErroresControlados() {
        Debug.info(ORIGEN, "las 3 líneas ERROR siguientes son provocadas a propósito");
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        Cola<String> cola = new Cola<>();
        verificar("obtener fuera de rango", lista.obtener(5) == null, "no devolvió null");
        verificar("desencolar vacía", cola.desencolar() == null, "no devolvió null");
        verificar("insertar fuera de rango", !lista.insertarEn(-1, "Z") && lista.estaVacia(), lista.toString());
    }

    /** Los enums exponen sus nombres legibles y propiedades. */
    private static void probarEnums() {
        verificar("EstadoProceso", EstadoProceso.values().length == 5 && EstadoProceso.EJECUCION.getNombre().equals("Ejecución"), "");
        verificar("TipoProceso.usaBuffer", TipoProceso.PRODUCTOR.usaBuffer() && !TipoProceso.CPU_BOUND.usaBuffer(), "");
        verificar("PoliticaTipo.esApropiativa", !PoliticaTipo.FCFS.esApropiativa() && PoliticaTipo.PRIORIDADES.esApropiativa(), "");
        verificar("ModoCPU", ModoCPU.SISTEMA_OPERATIVO.getNombre().equals("Sistema operativo"), "");
        verificar("MotivoBloqueo", MotivoBloqueo.valueOf("SEMAFORO") == MotivoBloqueo.SEMAFORO, "");
    }

    /** El log guarda eventos y devuelve los últimos. */
    private static void probarLog(LogEventos log) {
        int antes = log.tamano();
        log.registrar(0, "Computador 1 selecciona el proceso P3-H1");
        log.registrar(0, "Proceso P5-H1 se bloquea en el semáforo 'lleno' del buffer B1");
        verificar("log registra", log.tamano() == antes + 2, String.valueOf(log.tamano()));
        verificar("log últimos", log.ultimos(1, "|").contains("P5-H1"), log.ultimos(1, "|"));
        verificar("log copiar", log.copiar().length == log.tamano(), "");
    }

    /** Imprime OK si la condición se cumple; si no, ERROR con el detalle. */
    private static void verificar(String prueba, boolean condicion, String detalle) {
        if (condicion) {
            Debug.ok(ORIGEN, prueba);
        } else {
            fallos++;
            Debug.error(ORIGEN, prueba + " -> obtenido: " + detalle);
        }
    }
}
