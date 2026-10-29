package avilaos.vista;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.enums.PoliticaTipo;
import avilaos.hardware.ObservadorReloj;
import avilaos.log.Debug;
import avilaos.so.DatosProceso;
import avilaos.sync.DatosBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Vista de terminal: imprime el estado al cerrar cada ciclo y recibe comandos por teclado (equivale a los botones de la GUI). */
public class ConsolaTerminal implements ObservadorReloj {

    private static final String ORIGEN = "Consola";
    private static final String AYUDA = """
             Comandos (escribir y pulsar Enter):
               pausa | seguir | stop          pausar, reanudar o detener la simulación
               estado                         tabla completa de procesos
               metricas                       throughput, CPU, respuesta, deadlines, equidad y buffers
               grafico [ruta]                 guarda la utilización de CPU como PNG (salida/utilizacion_cpu.png)
               politica <comp> <POLITICA>     ej. politica 0 EDF  (FCFS, EDF, ROUND_ROBIN, PRIORIDADES)
               quantum <comp> <n>             ej. quantum 1 4
               velocidad <ms>                 duración del ciclo, ej. velocidad 200
               maxciclos <n>                  detener al llegar a n ciclos (0 = sin límite)
               buffer <nombre> <capacidad> <comp>
                                              crea un buffer, ej. buffer B2 4 1
               proceso <tipo> <nombre> <prio> <mem> <deadline> <comp|-1> <datos del tipo>
                                              cpu: <instrucciones> | io: <instrucciones> <cadaES> <duracionES>
                                              productor|consumidor: <buffer> <ciclosPorElemento> <elementos>
                                              ej. proceso cpu Calc 3 50 200 -1 20
               guardar [ruta]                 guarda la configuración actual (config/avilaosconfigs.json)
               ayuda                         muestra esta lista""";

    private final Cluster cluster;

    /** Crea la consola sobre el clúster indicado. */
    public ConsolaTerminal(Cluster cluster) {
        this.cluster = cluster;
    }

    /** Se registra como observador del clúster y arranca el hilo que lee comandos. */
    public void iniciar() {
        cluster.agregarObservador(this);
        Thread lector = new Thread(this::leerComandos, "Hilo-Consola");
        lector.setDaemon(true);
        lector.start();
        Debug.bloque(ReporteTerminal.separador() + "\n" + AYUDA + "\n" + ReporteTerminal.separador());
    }

    /** Imprime el estado del clúster al cerrar cada ciclo. */
    @Override
    public void alCerrarCiclo(long ciclo) {
        if (Debug.isActivo()) {
            Debug.bloque(ReporteTerminal.estadoCiclo(cluster, ciclo));
        }
    }

    /** Ejecuta un comando de texto. Devuelve false si no es válido. */
    public boolean ejecutarComando(String linea) {
        String[] partes = linea.replace("﻿", "").trim().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) {
            return false;
        }
        String comando = partes[0].toLowerCase();
        try {
            switch (comando) {
                case "pausa" -> cluster.pausar();
                case "seguir" -> cluster.reanudar();
                case "stop" -> cluster.detener();
                case "estado" -> Debug.bloque(ReporteTerminal.separador() + "\n"
                        + ReporteTerminal.tablaProcesos(cluster.todosLosProcesos()) + ReporteTerminal.separador());
                case "metricas" -> Debug.bloque(ReporteTerminal.separador() + "\n"
                        + ReporteTerminal.tablaMetricas(cluster) + ReporteTerminal.separador());
                case "grafico" -> {
                    return GraficoUtilizacion.exportarPNG(cluster.getUtilizacion(),
                            partes.length > 1 ? partes[1] : GraficoUtilizacion.RUTA_POR_DEFECTO);
                }
                case "ayuda" -> Debug.bloque(AYUDA);
                case "politica" -> {
                    exigir(partes, 3);
                    PoliticaTipo tipo = politica(partes[2]);
                    return tipo != null && cluster.cambiarPolitica(Integer.parseInt(partes[1]), tipo);
                }
                case "quantum" -> {
                    exigir(partes, 3);
                    return cluster.cambiarQuantum(Integer.parseInt(partes[1]), Integer.parseInt(partes[2]));
                }
                case "velocidad" -> {
                    exigir(partes, 2);
                    return cluster.cambiarDuracionCiclo(Integer.parseInt(partes[1]));
                }
                case "maxciclos" -> {
                    exigir(partes, 2);
                    return cluster.cambiarMaxCiclos(Integer.parseInt(partes[1]));
                }
                case "buffer" -> {
                    exigir(partes, 4);
                    return cluster.crearBuffer(new DatosBuffer(partes[1], Integer.parseInt(partes[2]),
                            Integer.parseInt(partes[3]))) != null;
                }
                case "proceso" -> {
                    return crearProceso(partes);
                }
                case "guardar" -> {
                    return cluster.guardarConfiguracion(partes.length > 1 ? partes[1] : ArchivoConfiguracion.RUTA_POR_DEFECTO);
                }
                default -> {
                    Debug.error(ORIGEN, "comando desconocido '" + partes[0] + "'; escribe 'ayuda'");
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            Debug.error(ORIGEN, "'" + linea + "': se esperaba un número entero");
        } catch (IllegalArgumentException e) {
            Debug.error(ORIGEN, "'" + linea + "': " + e.getMessage());
        }
        return false;
    }

    /** Arma los datos del proceso según su tipo y se los pasa al clúster. */
    private boolean crearProceso(String[] partes) {
        exigir(partes, 8);
        String nombre = partes[2];
        int prioridad = Integer.parseInt(partes[3]);
        int memoria = Integer.parseInt(partes[4]);
        int deadline = Integer.parseInt(partes[5]);
        int computador = Integer.parseInt(partes[6]);
        DatosProceso datos;
        switch (partes[1].toLowerCase()) {
            case "cpu" -> datos = DatosProceso.cpuBound(nombre, prioridad, memoria, deadline,
                    Integer.parseInt(partes[7]), computador);
            case "io" -> {
                exigir(partes, 10);
                datos = DatosProceso.ioBound(nombre, prioridad, memoria, deadline, Integer.parseInt(partes[7]),
                        Integer.parseInt(partes[8]), Integer.parseInt(partes[9]), computador);
            }
            case "productor" -> {
                exigir(partes, 10);
                datos = DatosProceso.productor(nombre, prioridad, memoria, deadline, partes[7],
                        Integer.parseInt(partes[8]), Integer.parseInt(partes[9]), computador);
            }
            case "consumidor" -> {
                exigir(partes, 10);
                datos = DatosProceso.consumidor(nombre, prioridad, memoria, deadline, partes[7],
                        Integer.parseInt(partes[8]), Integer.parseInt(partes[9]), computador);
            }
            default -> {
                Debug.error(ORIGEN, "tipo '" + partes[1] + "' no existe (cpu, io, productor, consumidor)");
                return false;
            }
        }
        return cluster.crearProceso(datos) != null;
    }

    /** Lee líneas de la entrada estándar hasta que se cierre. */
    private void leerComandos() {
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (!linea.isBlank()) {
                    ejecutarComando(linea);
                }
            }
        } catch (IOException e) {
            Debug.error(ORIGEN, "no se pudo leer la entrada: " + e.getMessage());
        }
    }

    /** Lanza un error si faltan argumentos. */
    private static void exigir(String[] partes, int cantidad) {
        if (partes.length < cantidad) {
            throw new IllegalArgumentException("faltan datos; escribe 'ayuda' para ver el formato");
        }
    }

    /** Convierte el texto en política o informa el error. */
    private static PoliticaTipo politica(String texto) {
        for (PoliticaTipo tipo : PoliticaTipo.values()) {
            if (tipo.name().equalsIgnoreCase(texto)) {
                return tipo;
            }
        }
        Debug.error(ORIGEN, "política '" + texto + "' no existe (FCFS, EDF, ROUND_ROBIN, PRIORIDADES)");
        return null;
    }
}
