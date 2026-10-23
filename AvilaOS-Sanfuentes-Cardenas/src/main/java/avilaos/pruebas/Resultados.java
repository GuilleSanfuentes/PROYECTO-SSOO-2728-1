package avilaos.pruebas;

import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import avilaos.so.PCB;
import avilaos.vista.ReporteTerminal;

/** Acumula los resultados de una etapa de pruebas y los procesos creados en ella. */
final class Resultados {

    private static final String ORIGEN = "Pruebas";

    private final int etapa;
    private final String titulo;
    private final ListaEnlazada<String> hechos = new ListaEnlazada<>();
    private final ListaEnlazada<PCB> procesos = new ListaEnlazada<>();
    private String grupo;
    private int correctasGrupo;
    private int fallidasGrupo;
    private int correctas;
    private int fallidas;

    /** Inicia una etapa de pruebas. */
    Resultados(int etapa, String titulo) {
        this.etapa = etapa;
        this.titulo = titulo;
        Debug.info(ORIGEN, "===== " + titulo + " =====");
    }

    /** Abre un grupo de pruebas; cierra el anterior. */
    void grupo(String nombre) {
        cerrarGrupo();
        grupo = nombre;
    }

    /** Imprime OK si la condición se cumple; si no, ERROR con el detalle. */
    void verificar(String prueba, boolean condicion, String detalle) {
        if (condicion) {
            correctasGrupo++;
            Debug.ok(ORIGEN, prueba);
        } else {
            fallidasGrupo++;
            Debug.error(ORIGEN, prueba + " -> obtenido: " + detalle);
        }
    }

    /** Guarda el proceso para mostrarlo en el resumen; devuelve el mismo PCB. */
    PCB registrar(PCB pcb) {
        if (pcb != null) {
            procesos.agregarFinal(pcb);
        }
        return pcb;
    }

    /** Cierra la etapa e imprime el resumen gráfico. */
    void cerrar() {
        cerrarGrupo();
        if (fallidas == 0) {
            Debug.ok(ORIGEN, titulo + ": sin fallos");
        } else {
            Debug.error(ORIGEN, titulo + ": " + fallidas + " fallos");
        }
        ReporteTerminal.resumenEtapa(etapa, titulo, hechos, procesos, correctas, fallidas);
    }

    /** Pasa el conteo del grupo actual a la lista de lo hecho. */
    private void cerrarGrupo() {
        if (grupo == null) {
            return;
        }
        int total = correctasGrupo + fallidasGrupo;
        hechos.agregarFinal((fallidasGrupo == 0 ? "[OK] " : "[!!] ") + grupo + ": " + correctasGrupo + "/" + total
                + (fallidasGrupo == 0 ? "" : " (" + fallidasGrupo + " fallidas)"));
        correctas += correctasGrupo;
        fallidas += fallidasGrupo;
        correctasGrupo = 0;
        fallidasGrupo = 0;
        grupo = null;
    }
}
